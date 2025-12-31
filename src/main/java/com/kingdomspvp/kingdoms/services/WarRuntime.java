// services/WarRuntime.java
package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import com.kingdomspvp.kingdoms.utils.KingdomsConfig;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WarRuntime {

    // Réglages
    public static int WAR_DURATION_SECONDS = 150; // 2min30

    // Rounds
    private static final int MAX_ROUNDS = 4;
    /** Réduction de gain par round pour approcher la même durée totale qu'avec d’anciens rounds de 10min. */
    private static final double ROUND_GAIN_FACTOR = 0.87; // ~ -13% par round

    /** Cible fixe : 1000 pts pour “capturer”. */
    private static final double TARGET_POINTS = 1000.0;
    /** On veut atteindre 1000 pts en 80% de la durée (ratio = 1). */
    private static final double TARGET_FRACTION = 0.80;
    private static final double BASE_TIME_AT_RATIO_1 = WAR_DURATION_SECONDS * TARGET_FRACTION; // 120s
    /** Gain de base (pts/s) quand ratio = 1 et round 1. */
    private static final double BASE_RATE = TARGET_POINTS / BASE_TIME_AT_RATIO_1; // ≈ 8.333 pts/s

    /** Bornes de ratio → 60..240s */
    private static final double RATIO_MIN = 0.5;  // 240s
    private static final double RATIO_MAX = 2.0;  // 60s


    public static void onDetectionWindowStarted(War war, Duration window) { getOrCreate(war).startDetectionWindow(window); }
    public static void onDetectionWindowEnded(War war) { getOrCreate(war).endDetectionWindow(); }

    // API publique
    public static void begin(War war) { getOrCreate(war).onWarBegan(); }
    public static void onFirstClaimAttacked(War war, Claim attackedDefClaim) { getOrCreate(war).onFirstClaimAttacked(attackedDefClaim); }
    public static void tpToWar(String warId, Player p) {
        War war = WarManager.getWar(warId);
        if (war == null) { ChatUtil.sendWarMsg(p, org.bukkit.ChatColor.RED + "Guerre introuvable."); return; }
        getOrCreate(war).teleportPlayer(p);
    }
    public static void stopAll() {
        for (WarSession s : SESSIONS.values()) s.stop(true);
        SESSIONS.clear();
    }

    // Nettoyage UI au reload/redémarrage
    public static void clearSidebarFor(Player p) {
        if (p == null) return;
        org.bukkit.scoreboard.Scoreboard sb = p.getScoreboard();
        if (sb == null) return;

        org.bukkit.scoreboard.Objective side = sb.getObjective(DisplaySlot.SIDEBAR);
        if (side != null && side.getName() != null && side.getName().startsWith("sbwar_")) side.unregister();

        for (org.bukkit.scoreboard.Objective o : sb.getObjectives()) {
            String n = o.getName();
            if (n != null && n.startsWith("sbwar_")) o.unregister();
        }
        for (org.bukkit.scoreboard.Team t : new ArrayList<>(sb.getTeams())) {
            String n = t.getName();
            if (n != null && (n.startsWith("war_static_") || n.startsWith("war_line_"))) {
                try { t.unregister(); } catch (Throwable ignored) {}
            }
        }
    }
    public static void stopAllAndClearAllUIs() {
        stopAll();
        for (Player p : Bukkit.getOnlinePlayers()) clearSidebarFor(p);
    }

    // Victoire DEF automatique si la fenêtre de détection expire sans attaque
    public static void defendersAutoWinNoAttack(War war) { getOrCreate(war).endRoundDefendersNoAttack(); }

    // interne
    private static final Map<String, WarSession> SESSIONS = new ConcurrentHashMap<>();
    private static WarSession getOrCreate(War war) { return SESSIONS.computeIfAbsent(war.getId(), k -> new WarSession(war)); }

    public static void onPlayerJoinReattach(Player p) { for (WarSession s : SESSIONS.values()) s.attachUIIfEligible(p); }
    public static void onPlayerQuitCleanup(UUID id)    { for (WarSession s : SESSIONS.values()) s.detachUI(id); }

    public static void onPlayerJoinOrRespawn(org.bukkit.entity.Player p) {
        if (p == null) return;
        java.util.UUID id = p.getUniqueId();
        for (WarSession s : SESSIONS.values()) {
            s.detachUI(id);          // nettoie l'ancienne UI (si restée en mémoire)
            s.attachUIIfEligible(p); // recrée bossbar + scoreboard dédiés
        }
    }
    public static void onPlayerDeath(java.util.UUID id) {
        if (id == null) return;
        for (WarSession s : SESSIONS.values()) s.detachUI(id); // évite les refs fantômes
    }

    // WarRuntime.java
    public static void recordKill(java.util.UUID killer, java.util.UUID victim) {
        for (WarSession s : SESSIONS.values()) {
            War w = s.war;
            boolean kIn = w.getAttackerPlayers().contains(killer) || w.getDefenderPlayers().contains(killer);
            boolean vIn = w.getAttackerPlayers().contains(victim) || w.getDefenderPlayers().contains(victim);
            if (!kIn || !vIn || w.getStatus() != WarStatus.INPROGRESS) continue;

            // Opposants ?
            boolean killerIsAtk = w.getAttackerPlayers().contains(killer);
            boolean victimIsAtk = w.getAttackerPlayers().contains(victim);
            if (killerIsAtk == victimIsAtk) continue; // même camp -> ignore

            s.onKill(killer, victim);
        }
    }

    public static void recordAssist(java.util.UUID assister) {
        for (WarSession s : SESSIONS.values()) {
            War w = s.war;
            boolean in = w.getAttackerPlayers().contains(assister) || w.getDefenderPlayers().contains(assister);
            if (!in || w.getStatus() != WarStatus.INPROGRESS) continue;
            s.onAssist(assister);
        }
    }

    public static boolean forceEnd(String warId, boolean attackersWin) {
        if (warId == null) return false;
        War w = WarManager.getWar(warId);
        if (w == null) return false;
        WarSession s = getOrCreate(w);
        s.adminForceEnd(attackersWin);
        return true;
    }

    // ===================== SESSION =====================
    private static final class WarSession {
        private final War war;
        private final org.bukkit.plugin.Plugin plugin = com.massivecraft.factions.FactionsPlugin.getInstance();

        private double pointsAttackers = 0.0;
        private double targetPoints    = TARGET_POINTS; // S*
        private double roundGainMultiplier = 1.0;

        private int roundIndex = 1;               // 1..MAX_ROUNDS
        private boolean combatActive = false;     // timer combat (round) actif ?

        private int seconds = WAR_DURATION_SECONDS;
        private int taskId = -1;
        private Claim attackedClaim;

        private final Map<UUID, KDA> kdas = new ConcurrentHashMap<>();// S*
        private double U               = 1.0;

        private int A = 0, D = 0; // inscrits
        private int defendersKills = 0; // pour le bonus passif anti-turtle (réservé évolutions)

        private final Map<UUID, BossAndBoard> ui = new ConcurrentHashMap<>();

        private boolean inDetectionWindow = false;
        private int detectionSecondsLeft = 0;

        void startDetectionWindow(Duration window) {
            this.inDetectionWindow = true;
            this.detectionSecondsLeft = (int) Math.max(0, window.getSeconds());
            this.combatActive = false;
            this.attackedClaim = null;
            for (BossAndBoard bb : ui.values()) bb.hideBar();
        }

        void endDetectionWindow() { this.inDetectionWindow = false; }

        WarSession(War war) { this.war = war; }

        void onWarBegan() {
            this.A = Math.max(1, war.getAttackerPlayers().size());
            this.D = Math.max(1, war.getDefenderPlayers().size());
            startTicker();
        }

        void onFirstClaimAttacked(Claim c) {
            this.attackedClaim = c;
            this.seconds = WAR_DURATION_SECONDS;
            this.combatActive = true;
            this.targetPoints = TARGET_POINTS;   // objectif fixe
            this.pointsAttackers = 0.0;
        }

        void stop(boolean announce) {
            if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
            for (BossAndBoard bb : ui.values()) bb.destroy();
            ui.clear();
            if (announce) {
                String msg = ChatUtil.prefixWithWar(org.bukkit.ChatColor.GOLD + winnerLine());
                WarManager.sendToRegisteredPlayers(war, TextComponent.fromLegacyText(msg));
            }
        }

        private void startTicker() {
            if (taskId != -1) return;
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin,
                    () -> {
                        updateOnlineUIs();
                        tickScoringAndDisplays();

                        if (inDetectionWindow && !combatActive) {
                            if (detectionSecondsLeft > 0) detectionSecondsLeft--;
                            else inDetectionWindow = false;
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

            double percent = (targetPoints > 0.0)
                    ? (pointsAttackers / targetPoints * 100.0)
                    : 0.0;
            if (percent < 0.0) percent = 0.0;
            if (percent > 100.0) percent = 100.0;

            String atkName = ChatUtil.kingdomName(war.getAttackerKingdom());
            String defName = ChatUtil.kingdomName(war.getDefenderKingdom());

            String roundMsg = attackersWon
                    ? ChatUtil.prefixWithWar(
                    org.bukkit.ChatColor.GOLD + "Fin du round " + roundIndex + " — "
                            + org.bukkit.ChatColor.GREEN + "Victoire des attaquants ( " + atkName + " ) "
                            + org.bukkit.ChatColor.GRAY + "— Pourcentage capture: "
                            + org.bukkit.ChatColor.AQUA + String.format(java.util.Locale.US, "%.1f%%", percent)
                            + (attackersInstantWin ? org.bukkit.ChatColor.DARK_GRAY + " (objectif atteint)" : "")
            )
                    : ChatUtil.prefixWithWar(
                    org.bukkit.ChatColor.GOLD + "Fin du round " + roundIndex + " — "
                            + org.bukkit.ChatColor.RED + "Victoire des défenseurs (" + defName + ") "
                            + org.bukkit.ChatColor.GRAY + "— Pourcentage capture: "
                            + org.bukkit.ChatColor.AQUA + String.format(java.util.Locale.US, "%.1f%%", percent)
            );

            WarManager.sendToRegisteredPlayers(
                    war, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(roundMsg)
            );

            // ✔ TRANSFERT DU CLAIM SI LES ATTAQUANTS GAGNENT CE ROUND
            if (attackersWon && attackedClaim != null) {
                ClaimManager.transferClaimToKingdom(attackedClaim, war.getAttackerKingdom().getName());
            }

            // ✔ SI CE N’ÉTAIT PAS LE DERNIER ROUND → PRÉPARATION ROUND SUIVANT
            if (attackersWon && roundIndex < MAX_ROUNDS) {
                int nextRound = roundIndex + 1;

                WarManager.sendToAttackers(war, WarManager.buildRoundStartMessage(nextRound));
                WarManager.sendToDefenders(war, WarManager.buildRoundStartMessage(nextRound));
                WarManager.sendToAttackers(war, WarManager.buildWaitForRoundMessage(war.getDefenderKingdom()));

                roundIndex = nextRound;
                roundGainMultiplier *= ROUND_GAIN_FACTOR;

                this.combatActive = false;
                this.attackedClaim = null;
                this.pointsAttackers = 0.0;
                this.defendersKills = 0;

                WarManager.prepareNextRound(war, true);
                return; // ✔ NE PAS NETTOYER, LA GUERRE CONTINUE
            }

            // ✔ SI ON ARRIVE ICI → LA GUERRE EST FINIE (DERNIER ROUND OU DÉFAITE ATTAQUANTS)
            // --- CLEANUP FINAL ---
            com.kingdomspvp.kingdoms.utils.ClaimVisualization.stopWarOutlines(war);

            war.setStatus(WarStatus.ENDED);
            WarManager.getWars().put(war.getId(), war);

            for (UUID id : war.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) WarManager.clearSidebarFor(p);
            }
            for (UUID id : war.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) WarManager.clearSidebarFor(p);
            }

            stop(false); // ✔ coupe ticker + bossbars + UI proprement
        }



        private String winnerLine() { return winnerLine(false); }
        private String winnerLine(boolean attackersInstantWin) {
            String atk = ChatUtil.kingdomName(war.getAttackerKingdom());
            String def = ChatUtil.kingdomName(war.getDefenderKingdom());
            if (pointsAttackers >= targetPoints) return "Victoire des attaquants (Royaume " + atk + ")";
            return "Victoire des défenseurs (Royaume " + def + ")";
        }

        // ---- TP rejoindre ----
        void teleportPlayer(Player p) {
            if (war.getStatus() != WarStatus.INPROGRESS || !war.hasCombatStarted()) {
                ChatUtil.sendWarMsg(p, org.bukkit.ChatColor.RED + "Pas en phase de combat."); return;
            }
            if (!isRegistered(p.getUniqueId())) {
                ChatUtil.sendWarMsg(p, org.bukkit.ChatColor.RED + "Tu n'es pas inscrit à cette guerre."); return;
            }
            Kingdom pk = KingdomsManager.getKingdomOfPlayer(p);
            if (pk == null) { ChatUtil.sendWarMsg(p, org.bukkit.ChatColor.RED + "Royaume inconnu."); return; }
            if (attackedClaim == null) { ChatUtil.sendWarMsg(p, org.bukkit.ChatColor.RED + "La zone d'affrontement n'est pas prête."); return; }

            org.bukkit.Location tp;
            if (pk.equals(war.getDefenderKingdom())) {
                tp = getClaimCenter(attackedClaim);
            } else {
                Claim staging = WarManager.findAdjacentAttackerClaim(war, attackedClaim);
                if (staging == null) { ChatUtil.sendWarMsg(p, org.bukkit.ChatColor.RED + "Aucun point de ralliement attaquant adjacent."); return; }
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

        private void tickScoringAndDisplays() {
            if (inDetectionWindow && !combatActive) {
                for (var entry : ui.entrySet()) {
                    UUID id = entry.getKey();
                    Player p = org.bukkit.Bukkit.getPlayer(id);
                    if (p == null) continue;

                    entry.getValue().updateDetection(
                            detectionSecondsLeft,
                            "Attaquez un claim"
                    );
                }
                return;
            }

            if (attackedClaim != null) {
                // Effectifs inscrits dynamiques
                this.A = Math.max(1, war.getAttackerPlayers().size());
                this.D = Math.max(1, war.getDefenderPlayers().size());

                int aNow = attackersInClaimNow();
                int dNow = defendersInClaimNow();

                if (aNow > 0) {
                    // Fractions de présence (0..1)
                    double fA = (double) aNow / Math.max(1, A);
                    double fD = (double) dNow / Math.max(1, D);

                    // Règle voulue + cas particuliers :
                    // - ratio = fA / fD si dNow > 0
                    // - si dNow == 0 :
                    //      * si fA == 1.0  -> ratio = 2.0  (2/2 vs 0/2 → 60s)
                    //      * sinon          -> ratio = fA  (1/2 vs 0/2 → 0.5 → 240s)
                    double ratio;
                    if (dNow > 0) {
                        ratio = fA / fD;
                    } else {
                        ratio = (fA >= 1.0) ? 2.0 : fA;
                    }

                    // Bornage pour garantir 60..240 s
                    if (ratio < RATIO_MIN) ratio = RATIO_MIN;
                    if (ratio > RATIO_MAX) ratio = RATIO_MAX;

                    // Gain de points de cette seconde
                    double gainPerSecond = BASE_RATE * roundGainMultiplier * ratio;
                    pointsAttackers += gainPerSecond;
                }
            }

            int attackersCount = countOnline(war.getAttackerPlayers());
            int defendersCount = countOnline(war.getDefenderPlayers());

            for (var entry : ui.entrySet()) {
                UUID id = entry.getKey();
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p == null) continue;

                boolean isAttacker = war.getAttackerPlayers().contains(id);
                int allies  = isAttacker ? attackersCount : defendersCount;
                int enemies = isAttacker ? defendersCount : attackersCount;

                KDA k = kdas.getOrDefault(id, new KDA());
                String kdaStr = k.k + "/" + k.d + "/" + k.a;

                entry.getValue().update(
                        seconds,
                        allies,
                        enemies,
                        kdaStr,
                        pointsAttackers,
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

        private static World getOverworld() {
            for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == World.Environment.NORMAL) return w;
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        private static final int CLAIM_SIZE = ClaimManager.getActiveClaimSize();
        private static org.bukkit.Location getClaimCenter(Claim c) {
            World world = getOverworld(); if (world == null) return null;
            int cx = c.getGridX() * CLAIM_SIZE + CLAIM_SIZE / 2;
            int cz = c.getGridZ() * CLAIM_SIZE + CLAIM_SIZE / 2;
            int y  = world.getHighestBlockYAt(cx, cz);
            return new org.bukkit.Location(world, cx + 0.5, y + 1, cz + 0.5);
        }

        void onKill(UUID killer, UUID victim) {
            kdas.computeIfAbsent(killer, k -> new KDA()).k++;
            kdas.computeIfAbsent(victim, k -> new KDA()).d++;
            if (war.getDefenderPlayers().contains(killer)) defendersKills++;
        }
        void onAssist(UUID assister) { kdas.computeIfAbsent(assister, k -> new KDA()).a++; }

        private void endRoundDefendersNoAttack() {
            this.inDetectionWindow = false;
            endWar(false);
        }

        // WarRuntime.java — AJOUTS (dans la classe interne WarSession)
        void attachUIIfEligible(org.bukkit.entity.Player p) {
            if (p == null || !p.isOnline()) return;
            java.util.UUID id = p.getUniqueId();
            boolean registered = war.getAttackerPlayers().contains(id) || war.getDefenderPlayers().contains(id);
            if (!registered) return;
            ui.computeIfAbsent(id, k -> BossAndBoard.attachTo(p, seconds, war, targetPoints));
        }
        void detachUI(java.util.UUID id) {
            BossAndBoard bb = ui.remove(id);
            if (bb != null) bb.destroy();
        }

        private static int countOnline(Collection<java.util.UUID> ids) {
            int n = 0;
            for (java.util.UUID id : ids) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) n++;
            }
            return n;
        }

        void adminForceEnd(boolean attackersWin) {
            // Calcul % actuel (borne 0..100)
            double percent = (targetPoints > 0.0) ? (pointsAttackers / targetPoints * 100.0) : 0.0;
            if (percent < 0.0) percent = 0.0;
            if (percent > 100.0) percent = 100.0;

            String atkName = ChatUtil.kingdomName(war.getAttackerKingdom());
            String defName = ChatUtil.kingdomName(war.getDefenderKingdom());

            String roundMsg;
            if (attackersWin) {
                roundMsg = ChatUtil.prefixWithWar(
                        org.bukkit.ChatColor.GOLD + "Fin forcée — " +
                                org.bukkit.ChatColor.GREEN + "Victoire des attaquants ( " + atkName + " ) " +
                                org.bukkit.ChatColor.GRAY + "— Pourcentage capture: " +
                                org.bukkit.ChatColor.AQUA + String.format(java.util.Locale.US, "%.1f%%", percent)
                );
            } else {
                roundMsg = ChatUtil.prefixWithWar(
                        org.bukkit.ChatColor.GOLD + "Fin forcée — " +
                                org.bukkit.ChatColor.RED + "Victoire des défenseurs (" + defName + ") " +
                                org.bukkit.ChatColor.GRAY + "— Pourcentage capture: " +
                                org.bukkit.ChatColor.AQUA + String.format(java.util.Locale.US, "%.1f%%", percent)
                );
            }

            // Message aux inscrits
            WarManager.sendToRegisteredPlayers(war,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(roundMsg));

            // Transfert du claim si victoire attaquants et claim engagé
            if (attackersWin) {
                com.kingdomspvp.kingdoms.model.Claim attacked = WarManager.getAttackedClaim(war);
                if (attacked != null) {
                    com.kingdomspvp.kingdoms.services.ClaimManager.transferClaimToKingdom(
                            attacked, war.getAttackerKingdom().getName()
                    );
                }
            }

            // Arrêts visuels/états
            com.kingdomspvp.kingdoms.utils.ClaimVisualization.stopWarOutlines(war);
            war.setStatus(WarStatus.ENDED);
            WarManager.getWars().put(war.getId(), war);

            // Coupe le ticker & nettoie UI
            stop(false);
        }


        private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    }

    private static final class BossAndBoard {
        private final Player p;
        private final War war;
        private final org.bukkit.boss.BossBar bar;

        // Scoreboards
        private final org.bukkit.scoreboard.Scoreboard oldSb; // avant attache
        private final org.bukkit.scoreboard.Scoreboard mySb;  // dédié
        private final org.bukkit.scoreboard.Objective obj;
        private final String objName;
        private final String teamPrefix;

        // Lignes
        private final LineTimer timer;
        private final Line allies;
        private final Line enemies;
        private final Line kda;
        private final Line points;
        private final Line objective;

        static BossAndBoard attachTo(Player p, int seconds, War war, double targetPoints) {
            org.bukkit.boss.BossBar bar = Bukkit.createBossBar(
                    makeBossTitle(war, 0.0, targetPoints),
                    BarColor.WHITE,
                    BarStyle.SOLID
            );
            bar.setProgress(0.0);
            bar.addPlayer(p);
            bar.setVisible(true);

            // --- SCOREBOARD DÉDIÉ ---
            org.bukkit.scoreboard.Scoreboard old = p.getScoreboard();
            org.bukkit.scoreboard.Scoreboard sb  = Bukkit.getScoreboardManager().getNewScoreboard();

            String objName = "sbwar_" + p.getUniqueId().toString().substring(0, 8);
            String title   = makeSidebarTitle(war);
            org.bukkit.scoreboard.Objective obj = sb.registerNewObjective(objName, "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            org.bukkit.scoreboard.Objective hb = sb.getObjective("hb_health");
            if (hb == null) hb = sb.registerNewObjective("hb_health", "health", org.bukkit.ChatColor.RED + "❤");
            hb.setDisplaySlot(DisplaySlot.BELOW_NAME);

            String teamPrefix = objName + "_";

            addStaticLine(sb, obj, teamPrefix, 10, org.bukkit.ChatColor.DARK_GRAY + "────────────");
            LineTimer timer = new LineTimer(sb, obj, teamPrefix, 9, "⏳ Temps", formatMMSS(seconds));
            addStaticLine(sb, obj, teamPrefix, 8, "");
            Line allies  = new Line(sb, obj, teamPrefix, 7, "Alliés", "0");
            Line enemies = new Line(sb, obj, teamPrefix, 6, "Adversaires", "0");
            addStaticLine(sb, obj, teamPrefix, 5, "");
            Line kda     = new Line(sb, obj, teamPrefix, 4, "K/D/A", "0/0/0");
            Line points  = new Line(sb, obj, teamPrefix, 3, "Pourcentage capture", "0%");
            Line objective = new Line(sb, obj, teamPrefix, 2, "Objectif", "100%");
            addStaticLine(sb, obj, teamPrefix, 1, org.bukkit.ChatColor.GRAY + "kingdoms.example");

            p.setScoreboard(sb);

            // ⬇️ constructeur aligné avec l’appel
            return new BossAndBoard(p, war, bar, old, sb, obj, objName, teamPrefix, timer, allies, enemies, kda, points, objective);
        }

        // ⚠️ pas de param "sb" inutile ici ; ordre = (oldSb, mySb, obj, …)
        private BossAndBoard(Player p, War war, org.bukkit.boss.BossBar bar,
                             org.bukkit.scoreboard.Scoreboard oldSb,
                             org.bukkit.scoreboard.Scoreboard mySb,
                             org.bukkit.scoreboard.Objective obj,
                             String objName, String teamPrefix,
                             LineTimer timer, Line allies, Line enemies, Line kda, Line points, Line objective) {
            this.p = p; this.war = war; this.bar = bar;
            this.oldSb = oldSb; this.mySb = mySb; this.obj = obj;
            this.objName = objName; this.teamPrefix = teamPrefix;
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
                if (obj != null) {
                    org.bukkit.scoreboard.Objective o = mySb.getObjective(obj.getName());
                    if (o != null) o.unregister();
                }
                for (org.bukkit.scoreboard.Team t : new java.util.ArrayList<>(mySb.getTeams())) {
                    String n = t.getName();
                    if (n != null && n.startsWith(teamPrefix)) {
                        try { t.unregister(); } catch (Throwable ignored2) {}
                    }
                }
            } catch (Throwable ignored) {}

            try {
                if (p != null && p.isOnline()) {
                    if (oldSb != null) p.setScoreboard(oldSb);
                    else p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            } catch (Throwable ignored) {}
        }

        private static void addStaticLine(org.bukkit.scoreboard.Scoreboard sb,
                                          org.bukkit.scoreboard.Objective obj,
                                          String teamPrefix, int score, String text) {
            // entry invisible et unique
            String entry = org.bukkit.ChatColor.values()[Math.max(0,
                    Math.min(org.bukkit.ChatColor.values().length - 1, score))].toString();

            String teamName = teamPrefix + "static_" + score;
            org.bukkit.scoreboard.Team team = sb.getTeam(teamName);
            if (team != null) team.unregister();
            team = sb.registerNewTeam(teamName);
            team.addEntry(entry);
            obj.getScore(entry).setScore(score);

            team.setPrefix(text);   // ce qui s'affiche
            team.setSuffix("");     // rien
        }


        private static String formatMMSS(int total) { int m = total / 60, s = total % 60; return String.format("%d:%02d", m, s); }

        // remplace le constructeur de Line(...)
        private static class Line {
            private final org.bukkit.scoreboard.Team team;
            private final String entry;

            Line(org.bukkit.scoreboard.Scoreboard sb, org.bukkit.scoreboard.Objective obj,
                 String teamPrefix, int score, String label, String initial) {

                String teamName = teamPrefix + "line_" + score;
                org.bukkit.scoreboard.Team t = sb.getTeam(teamName);
                if (t != null) t.unregister();
                this.team = sb.registerNewTeam(teamName);

                this.team.setPrefix(org.bukkit.ChatColor.YELLOW + label + org.bukkit.ChatColor.GRAY + ": ");
                this.team.setSuffix(org.bukkit.ChatColor.WHITE + initial);

                // entry invisible et unique
                this.entry = org.bukkit.ChatColor.values()[Math.max(0,
                        Math.min(org.bukkit.ChatColor.values().length - 1, score))].toString();

                this.team.addEntry(entry);
                obj.getScore(entry).setScore(score);
            }

            void setValue(String v) { this.team.setSuffix(org.bukkit.ChatColor.WHITE + v); }
        }


        private static class LineTimer extends Line {
            LineTimer(org.bukkit.scoreboard.Scoreboard sb, org.bukkit.scoreboard.Objective obj,
                      String teamPrefix, int score, String label, String initial) {
                super(sb, obj, teamPrefix, score, label, initial);
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
        private static String fmt1(double v) { return String.format(java.util.Locale.US, "%.1f", v); }

        void updateDetection(int secondsLeft, String objectiveText) {
            if (p == null || !p.isOnline()) return;
            timer.setTime(formatMMSS(Math.max(0, secondsLeft)));
            allies.setValue("—");
            enemies.setValue("—");
            kda.setValue("0/0/0");
            points.setValue("—");
            objective.setValue(objectiveText != null ? objectiveText : "Attaquez un claim");
            hideBar();
        }

        void hideBar() { try { bar.setVisible(false); } catch (Throwable ignored) {} }
        void showBar() { try { bar.setVisible(true); } catch (Throwable ignored) {} }
    }

    private static final class KDA { int k, d, a; }

}
