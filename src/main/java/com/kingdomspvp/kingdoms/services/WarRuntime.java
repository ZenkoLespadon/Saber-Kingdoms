// services/WarRuntime.java
package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO : Mettre un délai max de 2 minutes  pour l'attaque d'un claim et l'afficher dans le scoreboard, et quand le délai est dépassée, donner la victoire aux défenseurs
public final class WarRuntime {

    // Réglages
    public static int WAR_DURATION_SECONDS = 5 * 60; // METTRE A 10 * 60 EN PROD

    // Réglages des rounds
    private static final int MAX_ROUNDS = 4;
    private static final double ROUND_GAIN_FACTOR = 0.80; // -20% de gains par round

    // cible à 95% du score both-full → ~9:30 pour 10:00 en 1v1
    private static final double TARGET_FRACTION   = 0.95;

    // P0=0.08, 10 min = 600s → échelle pour viser 1000 points en 10 min (1v1, U=1)
    private static final double TARGET_AT_10_MIN  = 1000.0;
    private static final double SCALE = TARGET_AT_10_MIN / (TARGET_FRACTION * 0.5 * 1 * WAR_DURATION_SECONDS);


    // Paramètres (cohérents avec la description)
    private static final double P0    = 0.08;  // pts/sec (présence max si 100% attaquants / 0% défenseurs)
    private static final double BETA  = 0.5;   // intensité underdog
    private static final double U_MIN = 0.7;
    private static final double U_MAX = 1.4;

    public static void onDetectionWindowStarted(War war, Duration window) {
        getOrCreate(war).startDetectionWindow(window);
    }

    public static void onDetectionWindowEnded(War war) {
        getOrCreate(war).endDetectionWindow();
    }

    // API publique
    public static void begin(War war) { getOrCreate(war).onWarBegan(); }
    public static void onFirstClaimAttacked(War war, Claim attackedDefClaim) { getOrCreate(war).onFirstClaimAttacked(attackedDefClaim); }
    public static void tpToWar(String warId, Player p) {
        War war = WarManager.getWar(warId);
        if (war == null) { p.sendMessage(org.bukkit.ChatColor.RED + "Guerre introuvable."); return; }
        getOrCreate(war).teleportPlayer(p);
    }
    public static void stopAll() {
        for (WarSession s : SESSIONS.values()) s.stop(true);
        SESSIONS.clear();
    }

    // Victoire DEF automatique si la fenêtre de détection expire sans attaque
    public static void defendersAutoWinNoAttack(War war) {
        getOrCreate(war).endRoundDefendersNoAttack();
    }


    // interne
    private static final Map<String, WarSession> SESSIONS = new ConcurrentHashMap<>();
    private static WarSession getOrCreate(War war) { return SESSIONS.computeIfAbsent(war.getId(), k -> new WarSession(war)); }

    // ===================== SESSION =====================
    private static final class WarSession {
        private final War war;
        private final org.bukkit.plugin.Plugin plugin = com.massivecraft.factions.FactionsPlugin.getInstance();

        private int roundIndex = 1;              // 1..MAX_ROUNDS
        private double roundGainMultiplier = 1.0; // 1.0, 0.8, 0.64, 0.512
        private boolean combatActive = false;     // timer combat (round) actif ?

        private int seconds = WAR_DURATION_SECONDS;
        private int taskId = -1;
        private Claim attackedClaim;

        private final Map<UUID, KDA> kdas = new ConcurrentHashMap<>();
        private double pointsAttackers = 0.0;
        private double targetPoints    = 1.0; // S*
        private double U               = 1.0;

        private int A = 0, D = 0; // inscrits
        private int defendersKills = 0; // pour le bonus passif anti-turtle

        private final Map<UUID, BossAndBoard> ui = new ConcurrentHashMap<>();

        private boolean inDetectionWindow = false;
        private int detectionSecondsLeft = 0;

        void startDetectionWindow(Duration window) {
            this.inDetectionWindow = true;
            this.detectionSecondsLeft = (int) Math.max(0, window.getSeconds());
            // On n’est PAS en combat tant que le premier claim n’est pas attaqué
            this.combatActive = false;
            this.attackedClaim = null;

            // Masquer la BossBar sur tous les joueurs inscrits pendant la détection
            for (BossAndBoard bb : ui.values()) {
                bb.hideBar();
            }
        }

        void endDetectionWindow() {
            this.inDetectionWindow = false;
        }

        WarSession(War war) { this.war = war; }

        void onWarBegan() {
            this.A = Math.max(1, war.getAttackerPlayers().size());
            this.D = Math.max(1, war.getDefenderPlayers().size());

            double rawU = Math.pow((double) D / Math.max(1, A), BETA);
            this.U = Math.max(U_MIN, Math.min(U_MAX, rawU));

            // Le timer/objectif réels sont initialisés au 1er claim attaqué
            startTicker();
        }



        void onFirstClaimAttacked(Claim c) {
            this.attackedClaim = c;

            // (Re)lance le round
            this.seconds = WAR_DURATION_SECONDS;
            this.combatActive = true;

            // Objectif inchangé (en %), mais les GAINS par seconde seront multipliés par roundGainMultiplier
            this.targetPoints = SCALE * TARGET_FRACTION * 0.5 * P0 * U * WAR_DURATION_SECONDS;

            // Reset score d’affichage pour le nouveau round
            this.pointsAttackers = 0.0;

            // UI : on laisse les mêmes BossBars/scoreboards, progression repartira de 0%
        }



        void stop(boolean announce) {
            if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
            for (BossAndBoard bb : ui.values()) bb.destroy();
            ui.clear();

            if (announce) {
                WarManager.sendToRegisteredPlayers(war,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(org.bukkit.ChatColor.GOLD + winnerLine()));
            }
        }

        private void startTicker() {
            if (taskId != -1) return;
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin,
                    () -> {
                        updateOnlineUIs();

                        // MAJ affichages + scoring
                        tickScoringAndDisplays();

                        // Décompte de la fenêtre de détection quand pas en combat
                        if (inDetectionWindow && !combatActive) {
                            if (detectionSecondsLeft > 0) detectionSecondsLeft--;
                            else inDetectionWindow = false; // sécurité si le timeout côté WarManager a fini
                        }

                        if (combatActive) {
                            if (pointsAttackers >= targetPoints) { endWar(true); return; }
                            if (seconds <= 0) { endWar(false); return; }
                            seconds--;
                        }
                    },
                    0L, 20L
            );
        }

        private void endWar(boolean attackersInstantWin) {
            final boolean attackersWon = (pointsAttackers >= targetPoints);

            // % de capture affiché (borné 0..100)
            double percent = (targetPoints > 0.0) ? (pointsAttackers / targetPoints * 100.0) : 0.0;
            if (percent < 0.0) percent = 0.0;
            if (percent > 100.0) percent = 100.0;

            String atkName = ChatUtil.kingdomName(war.getAttackerKingdom());
            String defName = ChatUtil.kingdomName(war.getDefenderKingdom());
            String roundMsg;
            if (attackersWon) {
                roundMsg = org.bukkit.ChatColor.GOLD + "Fin du round " + roundIndex + " — "
                        + org.bukkit.ChatColor.GREEN + "Victoire des attaquants ( " + atkName + " ) "
                        + org.bukkit.ChatColor.GRAY + "— Pourcentage capture: "
                        + org.bukkit.ChatColor.AQUA + String.format(java.util.Locale.US, "%.1f%%", percent)
                        + (attackersInstantWin ? org.bukkit.ChatColor.DARK_GRAY + " (objectif atteint)" : "");
            } else {
                roundMsg = org.bukkit.ChatColor.GOLD + "Fin du round " + roundIndex + " — "
                        + org.bukkit.ChatColor.RED + "Victoire des défenseurs (" + defName + ") "
                        + org.bukkit.ChatColor.GRAY + "— Pourcentage capture: "
                        + org.bukkit.ChatColor.AQUA + String.format(java.util.Locale.US, "%.1f%%", percent);
            }



            // Envoi (fin de round)
            WarManager.sendToRegisteredPlayers(
                    war,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(roundMsg)
            );

            // Si les attaquants ont gagné : transfert du claim capturé
            if (attackersWon && attackedClaim != null) {
                // Transfert
                ClaimManager.transferClaimToKingdom(attackedClaim, war.getAttackerKingdom().getName());
            }

            WarManager.prepareNextRound(war, attackersWon && roundIndex < MAX_ROUNDS);


            if (attackersWon && roundIndex < MAX_ROUNDS) {
                // Préparer round suivant (−20% de gains)
                roundIndex++;
                roundGainMultiplier *= ROUND_GAIN_FACTOR;

                // Reset état "entre deux rounds"
                this.combatActive = false;
                this.attackedClaim = null;
                this.pointsAttackers = 0.0;
                this.defendersKills = 0;

                WarManager.prepareNextRound(war, true);

                WarManager.sendToAttackers(war,
                        org.bukkit.ChatColor.GOLD + "Round " + roundIndex + " — Attaquez un nouveau claim pour commencer !");
                WarManager.sendToDefenders(war,
                        org.bukkit.ChatColor.RED + "Round " + roundIndex + " — Préparez la défense.");

                return;
            }

            war.setStatus(WarStatus.ENDED);
            WarManager.getWars().put(war.getId(), war);
            stop(false);
        }


        private String winnerLine() { return winnerLine(false); }

        private String winnerLine(boolean attackersInstantWin) {
            String atk = ChatUtil.kingdomName(war.getAttackerKingdom());
            String def = ChatUtil.kingdomName(war.getDefenderKingdom());
            if (pointsAttackers >= targetPoints) {
                return "Victoire des attaquants (Royaume " + atk + ")";
            }
            return "Victoire des défenseurs (Royaume " + def + ")";
        }


        // ---- TP rejoindre ----
        void teleportPlayer(Player p) {
            if (war.getStatus() != WarStatus.INPROGRESS || !war.hasCombatStarted()) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Pas en phase de combat."); return;
            }
            if (!isRegistered(p.getUniqueId())) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Tu n'es pas inscrit à cette guerre."); return;
            }
            Kingdom pk = getPlayerKingdom(p);
            if (pk == null) { p.sendMessage(org.bukkit.ChatColor.RED + "Royaume inconnu."); return; }
            if (attackedClaim == null) { p.sendMessage(org.bukkit.ChatColor.RED + "La zone d'affrontement n'est pas prête."); return; }

            org.bukkit.Location tp;
            if (pk.equals(war.getDefenderKingdom())) {
                tp = getClaimCenter(attackedClaim);
            } else {
                Claim staging = WarManager.findAdjacentAttackerClaim(war, attackedClaim);
                if (staging == null) { p.sendMessage(org.bukkit.ChatColor.RED + "Aucun point de ralliement attaquant adjacent."); return; }
                tp = WarManager.getStagingPointNearBorder(staging, attackedClaim, 10);
            }
            p.teleport(tp);
        }

        // ---- UI & scoring ----
        private void updateOnlineUIs() {
            Set<UUID> shouldHave = new HashSet<>();
            shouldHave.addAll(war.getAttackerPlayers());
            shouldHave.addAll(war.getDefenderPlayers());

            for (UUID id : shouldHave) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    ui.computeIfAbsent(id, k -> BossAndBoard.attachTo(p, seconds, war, targetPoints));
                }
            }
            ui.entrySet().removeIf(e -> {
                UUID id = e.getKey();
                Player p = Bukkit.getPlayer(id);
                boolean keep = p != null && p.isOnline() &&
                        (war.getAttackerPlayers().contains(id) || war.getDefenderPlayers().contains(id));
                if (!keep) e.getValue().destroy();
                return !keep;
            });
        }

        // Java
        private void tickScoringAndDisplays() {
            if (inDetectionWindow && !combatActive) {
                for (var entry : ui.entrySet()) {
                    UUID id = entry.getKey();
                    Player p = org.bukkit.Bukkit.getPlayer(id);
                    if (p == null) continue;

                    entry.getValue().updateDetection(
                            detectionSecondsLeft,
                            "Attaquez un claim" // objectif affiché
                    );
                }
                return; // ne pas exécuter la partie "combat"
            }

            if (attackedClaim != null) {
                int aNow = attackersInClaimNow();
                int dNow = defendersInClaimNow();

                // Les points n'augmentent que si au moins un attaquant est présent
                if (aNow > 0) {
                    double rA = clamp01((double) aNow / Math.max(1, A));
                    double rD = clamp01((double) dNow / Math.max(1, D));
                    double presenceGain = SCALE * P0 * U * clamp01((rA - rD + 1.0) / 2.0);
                    pointsAttackers += presenceGain;
                }
            }

            // 2) --- Mise à jour des affichages (scoreboard + bossbar) ---
            int attackersCount = war.getAttackerPlayers().size();
            int defendersCount = war.getDefenderPlayers().size();

            for (var entry : ui.entrySet()) {
                java.util.UUID id = entry.getKey();
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p == null) continue;

                boolean isAttacker = war.getAttackerPlayers().contains(id);
                int allies  = isAttacker ? attackersCount : defendersCount;
                int enemies = isAttacker ? defendersCount : attackersCount;

                // K/D/A individuel (affichage seulement)
                KDA k = kdas.getOrDefault(id, new KDA());
                String kdaStr = k.k + "/" + k.d + "/" + k.a;

                double ptsForSide = pointsAttackers; // on affiche l'objectif côté attaquants
                entry.getValue().update(
                        seconds,
                        allies,
                        enemies,
                        kdaStr,
                        ptsForSide,
                        pointsAttackers,
                        targetPoints
                );
            }
        }

        private int attackersInClaimNow() {
            if (attackedClaim == null) return 0;
            int gx = attackedClaim.getGridX(), gz = attackedClaim.getGridZ();
            int count = 0;
            for (UUID id : war.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) continue;
                if (isInGrid(p, gx, gz)) count++;
            }
            return count;
        }

        private int defendersInClaimNow() {
            if (attackedClaim == null) return 0;
            int gx = attackedClaim.getGridX(), gz = attackedClaim.getGridZ();
            int count = 0;
            for (UUID id : war.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) continue;
                if (isInGrid(p, gx, gz)) count++;
            }
            return count;
        }

        private boolean isInGrid(Player p, int gridX, int gridZ) {
            Claim c = ClaimManager.getClaimByCoordinates(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
            return c != null && c.getGridX() == gridX && c.getGridZ() == gridZ;
        }

        private boolean isRegistered(UUID id) {
            return war.getAttackerPlayers().contains(id) || war.getDefenderPlayers().contains(id);
        }

        private static Kingdom getPlayerKingdom(Player p) {
            FPlayer fp = FPlayers.getInstance().getByPlayer(p);
            if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) return null;
            return KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
        }

        // Overworld util
        private static World getOverworld() {
            for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == World.Environment.NORMAL) return w;
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        private static final int CLAIM_SIZE = ClaimManager.CLAIM_SIZE;
        private static org.bukkit.Location getClaimCenter(Claim c) {
            World world = getOverworld(); if (world == null) return null;
            int cx = c.getGridX() * CLAIM_SIZE + CLAIM_SIZE / 2;
            int cz = c.getGridZ() * CLAIM_SIZE + CLAIM_SIZE / 2;
            int y  = world.getHighestBlockYAt(cx, cz);
            return new org.bukkit.Location(world, cx + 0.5, y + 1, cz + 0.5);
        }

        // K/D/A (affichage) + détection des kills DEF pour le bonus passif
        void onKill(UUID killer, UUID victim) {
            kdas.computeIfAbsent(killer, k -> new KDA()).k++;
            kdas.computeIfAbsent(victim, k -> new KDA()).d++;
            if (war.getDefenderPlayers().contains(killer)) defendersKills++;
        }
        void onAssist(UUID assister) { kdas.computeIfAbsent(assister, k -> new KDA()).a++; }

        // Fin de round côté défenseurs (aucune attaque n'a commencé)
        private void endRoundDefendersNoAttack() {
            // Stop l'affichage "détection" si encore actif
            this.inDetectionWindow = false;
            // Termine le round comme une victoire DEF (équivaut à timer écoulé)
            endWar(false);
        }


        private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
        private String fmt(double v) { return String.format(java.util.Locale.US, "%.1f", v); }
    }

    // ===================== UI =====================
    private static final class BossAndBoard {
        private final Player p;
        private final War war;
        private final org.bukkit.boss.BossBar bar;
        private final org.bukkit.scoreboard.Scoreboard sb;
        private final org.bukkit.scoreboard.Objective obj;

        private final LineTimer timer;
        private final Line allies;
        private final Line enemies;
        private final Line kda;
        private final Line points;
        private final Line objective;

        static BossAndBoard attachTo(Player p, int seconds, War war, double targetPoints) {
            // BossBar (comme avant)
            org.bukkit.boss.BossBar bar = Bukkit.createBossBar(
                    makeBossTitle(war, 0.0, targetPoints),
                    BarColor.WHITE,
                    BarStyle.SOLID
            );
            bar.setProgress(0.0);
            bar.addPlayer(p);
            bar.setVisible(true);

            // *** IMPORTANT *** : on n'écrase PAS le scoreboard du joueur
            org.bukkit.scoreboard.Scoreboard sb = p.getScoreboard();
            if (sb == null) sb = Bukkit.getScoreboardManager().getMainScoreboard();

            // Objectif SIDEBAR dédié à ce joueur pour éviter tout conflit global
            String objName = "sbwar_" + p.getUniqueId().toString().substring(0, 8);
            org.bukkit.scoreboard.Objective old = sb.getObjective(objName);
            if (old != null) old.unregister();

            String title = makeSidebarTitle(war);
            org.bukkit.scoreboard.Objective obj = sb.registerNewObjective(objName, "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            // *** PRÉSERVATION HEALTHBAR ***
            // Si aucun objectif BELOW_NAME n'est affiché, on en crée un basé sur le critère "health".
            // Si HealthBar-Reloaded gère déjà BELOW_NAME, on NE TOUCHE A RIEN.
            if (sb.getObjective(DisplaySlot.BELOW_NAME) == null) {
                org.bukkit.scoreboard.Objective hb = sb.getObjective("hb_health");
                if (hb == null) {
                    hb = sb.registerNewObjective("hb_health", "health", org.bukkit.ChatColor.RED + "❤");
                }
                hb.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }

            // Lignes (teams) — noms préfixés "war_" pour pouvoir les nettoyer sans toucher aux autres plugins
            addStaticLine(sb, obj, 10, org.bukkit.ChatColor.DARK_GRAY + "────────────");
            LineTimer timer = new LineTimer(sb, obj, 9, "⏳ Temps", formatMMSS(seconds));
            addStaticLine(sb, obj, 8, "");
            Line allies  = new Line(sb, obj, 7, "Alliés", "0");
            Line enemies = new Line(sb, obj, 6, "Adversaires", "0");
            addStaticLine(sb, obj, 5, "");
            Line kda     = new Line(sb, obj, 4, "K/D/A", "0/0/0");
            Line points  = new Line(sb, obj, 3, "Pourcentage capture", "0%");
            Line objective = new Line(sb, obj, 2, "Objectif", "100%");
            addStaticLine(sb, obj, 1, org.bukkit.ChatColor.GRAY + "kingdoms.example");

            // *** NE PAS faire p.setScoreboard(sb); ***
            return new BossAndBoard(p, war, bar, sb, obj, timer, allies, enemies, kda, points, objective);
        }


        private BossAndBoard(Player p, War war, org.bukkit.boss.BossBar bar,
                             org.bukkit.scoreboard.Scoreboard sb, org.bukkit.scoreboard.Objective obj,
                             LineTimer timer, Line allies, Line enemies, Line kda, Line points, Line objective) {
            this.p = p; this.war = war; this.bar = bar; this.sb = sb; this.obj = obj;
            this.timer = timer; this.allies = allies; this.enemies = enemies; this.kda = kda; this.points = points; this.objective = objective;
        }

        void update(int seconds, int alliesCount, int enemiesCount, String kdaStr,
                    double sidePoints, double attackerPoints, double targetPoints) {
            if (p == null || !p.isOnline()) return;

            showBar();

            timer.setTime(formatMMSS(seconds));
            allies.setValue(String.valueOf(alliesCount));
            enemies.setValue(String.valueOf(enemiesCount));
            kda.setValue(kdaStr);

            // Calcul du pourcentage
            double percent = (targetPoints > 0.0) ? (attackerPoints / targetPoints * 100.0) : 0.0;
            if (percent > 100.0) percent = 100.0;

            points.setValue(String.format("%.1f%%", percent));
            objective.setValue("100%");

            double progress = percent / 100.0;
            bar.setProgress(progress);
            bar.setColor(progress > 0.0 ? mapColor(war.getAttackerKingdom()) : BarColor.WHITE);
            bar.setTitle(org.bukkit.ChatColor.GOLD + "Pourcentage capture: "
                    + org.bukkit.ChatColor.WHITE + String.format("%.1f%%", percent));
        }


        void destroy() {
            try { bar.removeAll(); } catch (Throwable ignored) {}

            try {
                // Supprime notre objectif SIDEBAR dédié
                if (obj != null) {
                    org.bukkit.scoreboard.Objective o = sb.getObjective(obj.getName());
                    if (o != null) o.unregister();
                }

                // Supprime UNIQUEMENT nos teams (préfixes "war_static_" et "war_line_")
                for (org.bukkit.scoreboard.Team t : new java.util.ArrayList<>(sb.getTeams())) {
                    String n = t.getName();
                    if (n != null && (n.startsWith("war_static_") || n.startsWith("war_line_"))) {
                        try { t.unregister(); } catch (Throwable ignored2) {}
                    }
                }
            } catch (Throwable ignored) {}

            // *** NE PAS remettre le main scoreboard ici ***
            // (On laisse le scoreboard en place pour ne pas casser HealthBar-Reloaded ou d'autres plugins.)
        }


        private static void addStaticLine(org.bukkit.scoreboard.Scoreboard sb,
                                          org.bukkit.scoreboard.Objective obj,
                                          int score, String text) {
            String entry = text + org.bukkit.ChatColor.values()[Math.max(0, Math.min(org.bukkit.ChatColor.values().length - 1, score))];
            String teamName = "war_static_" + score;
            org.bukkit.scoreboard.Team team = sb.getTeam(teamName);
            if (team != null) team.unregister();
            team = sb.registerNewTeam(teamName);
            team.addEntry(entry);
            obj.getScore(entry).setScore(score);
        }


        private static String formatMMSS(int total) {
            int m = total / 60, s = total % 60; return String.format("%d:%02d", m, s);
        }

        private static class Line {
            private final org.bukkit.scoreboard.Team team; private final String entry;
            Line(org.bukkit.scoreboard.Scoreboard sb, org.bukkit.scoreboard.Objective obj, int score, String label, String initial) {
                String teamName = "war_line_" + score;
                org.bukkit.scoreboard.Team t = sb.getTeam(teamName);
                if (t != null) t.unregister();
                this.team = sb.registerNewTeam(teamName);
                this.team.setPrefix(org.bukkit.ChatColor.YELLOW + label + org.bukkit.ChatColor.GRAY + ": ");
                this.team.setSuffix(org.bukkit.ChatColor.WHITE + initial);
                this.entry = org.bukkit.ChatColor.values()[score].toString();
                this.team.addEntry(entry); obj.getScore(entry).setScore(score);
            }
            void setValue(String v) { this.team.setSuffix(org.bukkit.ChatColor.WHITE + v); }
        }

        private static class LineTimer extends Line {
            LineTimer(org.bukkit.scoreboard.Scoreboard sb, org.bukkit.scoreboard.Objective obj, int score, String label, String initial) {
                super(sb, obj, score, label, initial);
            }
            void setTime(String mmss) { setValue(mmss); }
        }

        private static BarColor mapColor(Kingdom k) {
            org.bukkit.ChatColor c = k.getColor();
            if (c == null) return BarColor.WHITE;
            switch (c) {
                case RED: return BarColor.RED;
                case GREEN: return BarColor.GREEN;
                case YELLOW: return BarColor.YELLOW;
                case BLUE: return BarColor.BLUE;
                case LIGHT_PURPLE:
                case DARK_PURPLE: return BarColor.PURPLE;
                default: return BarColor.WHITE;
            }
        }
        private static String makeSidebarTitle(War war) {
            org.bukkit.ChatColor atk = war.getAttackerKingdom().getColor();
            org.bukkit.ChatColor def = war.getDefenderKingdom().getColor();
            return atk + "" + org.bukkit.ChatColor.BOLD + war.getAttackerKingdom().getName()
                    + org.bukkit.ChatColor.GRAY + " vs "
                    + def + "" + org.bukkit.ChatColor.BOLD + war.getDefenderKingdom().getName();
        }
        private static String makeBossTitle(War war, double attackerPoints, double targetPoints) {
            return makeSidebarTitle(war) + org.bukkit.ChatColor.GRAY + "  [" + fmt1(attackerPoints) + "/" + fmt1(targetPoints) + "]";
        }
        private static String makeBossTitleForProgress(double attackerPoints, double targetPoints) {
            return org.bukkit.ChatColor.GOLD + "Progression attaquants: "
                    + org.bukkit.ChatColor.WHITE + fmt1(attackerPoints)
                    + org.bukkit.ChatColor.GRAY + " / "
                    + org.bukkit.ChatColor.AQUA + fmt1(targetPoints);
        }

        void updateDetection(int secondsLeft, String objectiveText) {
            if (p == null || !p.isOnline()) return;

            // Scoreboard uniquement (pas de bossbar en détection)
            timer.setTime(formatMMSS(Math.max(0, secondsLeft)));
            allies.setValue("—");
            enemies.setValue("—");
            kda.setValue("0/0/0");
            points.setValue("—");
            objective.setValue(objectiveText != null ? objectiveText : "Attaquez un claim");

            // Assure que la BossBar reste cachée
            hideBar();
        }

        void hideBar() {
            try { bar.setVisible(false); } catch (Throwable ignored) {}
        }

        void showBar() {
            try { bar.setVisible(true); } catch (Throwable ignored) {}
        }


        private static String fmt1(double v) { return String.format(java.util.Locale.US, "%.1f", v); }
    }

    // ===================== STATS =====================
    private static final class KDA { int k, d, a; }
}
