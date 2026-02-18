// services/WarRuntime.java
package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import com.kingdomspvp.kingdoms.utils.PowerCalculator;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
import com.massivecraft.factions.integration.Econ;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WarRuntime {

    // Durée d’un round
    public static int WAR_DURATION_SECONDS;

    // Rounds
    private static int MAX_ROUNDS;
    private static double ROUND_GAIN_FACTOR;

    // Scoring
    private static double TARGET_POINTS;

    private static double BASE_RATE;

    // Ratios
    private static double RATIO_MIN;
    private static double RATIO_MAX;

    private static int TP_OFFSET_FROM_BORDER;
    private static int TP_Y_OFFSET;

    // TODO : A mettre dans le fichier de config
    private static final long BASE_ATK_REWARD_PER_ROUND = 1000;
    private static final long BASE_DEF_REWARD_PER_ROUND = 1500;
    private static final long NO_ROUND_ATK_REWARD = 100;
    private static final long NO_ROUND_DEF_REWARD = 1000;



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

    public static void reloadSettings() {

        var war = SettingsProvider.get().war();

        WAR_DURATION_SECONDS = war.combat().durationSeconds();
        MAX_ROUNDS = war.combat().maxRounds();
        ROUND_GAIN_FACTOR = war.combat().roundGainFactor();

        TARGET_POINTS = war.combat().targetPoints();
        double TARGET_FRACTION = war.combat().targetFraction();

        RATIO_MIN = war.combat().ratioMin();
        RATIO_MAX = war.combat().ratioMax();

        // --- Valeurs dérivées (TOUJOURS recalculées) ---
        double BASE_TIME_AT_RATIO_1 = WAR_DURATION_SECONDS * TARGET_FRACTION;

        // Sécurité anti-division par zéro
        if (BASE_TIME_AT_RATIO_1 <= 0.0) {
            BASE_TIME_AT_RATIO_1 = 1.0;
        }

        BASE_RATE = TARGET_POINTS / BASE_TIME_AT_RATIO_1;

        TP_OFFSET_FROM_BORDER = war.teleport().offsetFromBorder();
        TP_Y_OFFSET = war.teleport().yOffset();
    }


    // ===================== SESSION =====================
    private static final class WarSession {
        private final War war;
        private final org.bukkit.plugin.Plugin plugin = com.massivecraft.factions.FactionsPlugin.getInstance();

        private double pointsAttackers = 0.0;
        private double roundGainMultiplier = 1.0;

        private int secondsLeft;
        private double targetPoints;

        // TODO : A retirer après la beta
        private final Map<UUID, Integer> presenceTicks = new ConcurrentHashMap<>();
        private final Map<UUID, Double> contributionTicks = new ConcurrentHashMap<>();
        private int tickCounter = 0;


        private int roundIndex = 1;               // 1..MAX_ROUNDS
        private boolean combatActive = false;     // timer combat (round) actif ?

        private int taskId = -1;
        private Claim attackedClaim;

        private final Map<UUID, KDA> kdas = new ConcurrentHashMap<>();// S*
        private double U               = 1.0;

        private int A = 0, D = 0; // inscrits
        private int defendersKills = 0; // pour le bonus passif anti-turtle (réservé évolutions)

        // --- Glow périodique ---
        private static final int GLOW_INTERVAL_SECONDS = 15;
        private static final int GLOW_DURATION_SECONDS = 5;

        private int glowCooldown = GLOW_INTERVAL_SECONDS;
        private final Set<UUID> glowingNow = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());


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
            this.combatActive = true;

            this.secondsLeft = WAR_DURATION_SECONDS;
            this.targetPoints = TARGET_POINTS;

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

                        // --- Glow périodique pendant la guerre (quand la guerre est en cours) ---
                        handleGlowPulse();

                        if (combatActive) {
                            if (pointsAttackers >= targetPoints) { endWar(true); return; }
                            if (secondsLeft <= 0) { endWar(false); return; }
                            secondsLeft--;
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

            war.incrementRoundsPlayed();


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
                ClaimManager.transferClaimToKingdom(attackedClaim, true, war.getAttackerKingdom().getName());
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

            // === RECOMPENSES ===

            int r = war.getRoundsPlayed();

            long atkBonus = r * BASE_ATK_REWARD_PER_ROUND;
            long defBonus = r * BASE_DEF_REWARD_PER_ROUND;

            for (UUID id : war.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    if (atkBonus > 0) {
                        Econ.modifyBalance(p.getName(), atkBonus);
                        p.sendMessage(ChatColor.GOLD + "Bonus rounds : " + atkBonus + "$");
                    }
                }
            }

            for (UUID id : war.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    if (defBonus > 0) {
                        Econ.modifyBalance(p.getName(), defBonus);
                        p.sendMessage(ChatColor.GOLD + "Bonus rounds : " + defBonus + "$");
                    }
                }
            }


            int playerCount = war.getAttackerPlayers().size() + war.getDefenderPlayers().size();
            if (playerCount < 1) playerCount = 1;

            // base reward = 1000$/joueur avec réduction légère
            double baseReward = 1000.0 * playerCount / (1.0 + playerCount * 0.02);

            // top contribution pour normaliser
            double topContribution = 0.0;
            for (double v : contributionTicks.values()) {
                if (v > topContribution) topContribution = v;
            }
            if (topContribution <= 0) topContribution = 1.0;

            for (UUID id : war.getAttackerPlayers()) {
                distributeRewardTo(id, true, baseReward, topContribution);
            }
            for (UUID id : war.getDefenderPlayers()) {
                distributeRewardTo(id, false, baseReward, topContribution);
            }


            stop(false); // ✔ coupe ticker + bossbars + UI proprement
        }

        private void distributeRewardTo(UUID id,
                                        boolean isAttacker,
                                        double baseReward,
                                        double topContribution) {

            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) return;

            // --- presence / contribution ---
            double contrib = contributionTicks.getOrDefault(id, 0.0);
            double presenceScore = contrib / topContribution;
            if (presenceScore < 0) presenceScore = 0;
            if (presenceScore > 1) presenceScore = 1;

            // --- performance round ---
            double roundPerf;
            if (isAttacker) {
                roundPerf = (double) (roundIndex - 1) / MAX_ROUNDS;
            } else {
                roundPerf = (double) (MAX_ROUNDS - (roundIndex - 1)) / MAX_ROUNDS;
            }
            if (roundPerf < 0) roundPerf = 0;
            if (roundPerf > 1) roundPerf = 1;

            // --- KDA ---
            KDA k = kdas.getOrDefault(id, new KDA());
            double kdar = (k.k + k.a * 0.5) / Math.max(1, k.d);
            double kdaScore = kdar / 5.0;
            if (kdaScore < 0) kdaScore = 0;
            if (kdaScore > 1) kdaScore = 1;

            // --- score final ---
            double finalScore =
                    presenceScore * 0.30 +
                            roundPerf     * 0.30 +
                            kdaScore      * 0.40;

            if (finalScore < 0) finalScore = 0;
            if (finalScore > 1) finalScore = 1;

            long reward = Math.round(baseReward * finalScore);

            p.sendMessage(org.bukkit.ChatColor.GOLD + "Récompense : " + reward + "$");
            Econ.modifyBalance(p.getName(), reward);
        }



        private String winnerLine() { return winnerLine(false); }
        private String winnerLine(boolean attackersInstantWin) {
            String atk = ChatUtil.kingdomName(war.getAttackerKingdom());
            String def = ChatUtil.kingdomName(war.getDefenderKingdom());
            if (pointsAttackers >= targetPoints) return "Victoire des attaquants (Royaume " + atk + ")";
            return "Victoire des défenseurs (Royaume " + def + ")";
        }


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
                tp = WarManager.getStagingPointNearBorder(staging, attackedClaim, TP_OFFSET_FROM_BORDER, TP_Y_OFFSET);
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
                    ui.computeIfAbsent(id, k -> BossAndBoard.attachTo(p, secondsLeft, war, targetPoints));
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
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null) continue;
                    entry.getValue().updateDetection(detectionSecondsLeft, "Attaquez un claim");
                }
                return;
            }

            // // TODO : A retirer après la beta
            if (WarManager.isTestMode() && attackedClaim != null) {

                int gx = attackedClaim.getGridX();
                int gz = attackedClaim.getGridZ();

                // présence
                for (UUID id : war.getAttackerPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && isInGrid(p, gx, gz)) {
                        presenceTicks.merge(id, 1, Integer::sum);
                    }
                }
                for (UUID id : war.getDefenderPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && isInGrid(p, gx, gz)) {
                        presenceTicks.merge(id, 1, Integer::sum);
                    }
                }
            }

            // === SCORING NORMAL (inchangé) ===
            if (attackedClaim != null) {

                int gx = attackedClaim.getGridX();
                int gz = attackedClaim.getGridZ();

                List<Integer> atkPowers = new ArrayList<>();
                List<Integer> defPowers = new ArrayList<>();

                List<UUID> atkPresent = new ArrayList<>();
                List<UUID> defPresent = new ArrayList<>();

                for (UUID id : war.getAttackerPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && isInGrid(p, gx, gz)) {
                        atkPresent.add(id);
                        atkPowers.add(war.getPlayerPower(id));
                    }
                }
                for (UUID id : war.getDefenderPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && isInGrid(p, gx, gz)) {
                        defPresent.add(id);
                        defPowers.add(war.getPlayerPower(id));
                    }
                }

                int aNow = atkPresent.size();
                int dNow = defPresent.size();

                if (aNow > 0) {

                    double effA = PowerCalculator.effectivePower(atkPowers);
                    double effD = PowerCalculator.effectivePower(defPowers);

                    double ratio = (effD > 0 ? effA / effD : RATIO_MAX);
                    if (ratio < RATIO_MIN) ratio = RATIO_MIN;
                    if (ratio > RATIO_MAX) ratio = RATIO_MAX;

                    double gainPerSecond = BASE_RATE * roundGainMultiplier * ratio;
                    pointsAttackers += gainPerSecond;

                    // TODO : A retirer après la beta
                    if (WarManager.isTestMode()) {

                        double totalAtkPower = atkPowers.stream().mapToDouble(i -> i).sum();
                        if (totalAtkPower > 0) {
                            for (UUID id : atkPresent) {
                                double pwr = war.getPlayerPower(id);
                                double share = (pwr / totalAtkPower) * gainPerSecond;
                                contributionTicks.merge(id, share, Double::sum);
                            }
                        }
                    }
                }
            }

            // TODO : A retirer après la beta
            if (WarManager.isTestMode()) {
                tickCounter++;
                if (tickCounter >= 10 && attackedClaim != null) {
                    logContributions10s(attackedClaim);
                    tickCounter = 0;
                    presenceTicks.clear();
                    contributionTicks.clear();
                }
            }

            // === UI inchangé ===
            int attackersCount = countOnline(war.getAttackerPlayers());
            int defendersCount = countOnline(war.getDefenderPlayers());

            for (var entry : ui.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null) continue;

                boolean isAttacker = war.getAttackerPlayers().contains(entry.getKey());
                int allies = isAttacker ? attackersCount : defendersCount;
                int enemies = isAttacker ? defendersCount : attackersCount;

                KDA k = kdas.getOrDefault(entry.getKey(), new KDA());
                String kdaStr = k.k + "/" + k.d + "/" + k.a;

                entry.getValue().update(
                        secondsLeft,
                        allies,
                        enemies,
                        kdaStr,
                        pointsAttackers,
                        pointsAttackers,
                        targetPoints
                );
            }
        }

        private void handleGlowPulse() {
            // On ne glow que si la guerre est réellement en cours (status INPROGRESS)
            if (war.getStatus() != WarStatus.INPROGRESS) return;

            // Cooldown entre deux pulses
            if (glowCooldown > 0) {
                glowCooldown--;
                return;
            }

            // Lancement d'un pulse
            glowCooldown = GLOW_INTERVAL_SECONDS;
            startGlowPulse();
        }


        private void startGlowPulse() {
            // Active le glow sur tous les joueurs inscrits (ATK + DEF)
            for (UUID id : war.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    p.setGlowing(true);
                    glowingNow.add(id);
                }
            }
            for (UUID id : war.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    p.setGlowing(true);
                    glowingNow.add(id);
                }
            }

            // Planifie l'arrêt du glow au bout de 5 secondes
            Bukkit.getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    this::stopGlowPulse,
                    GLOW_DURATION_SECONDS * 20L
            );
        }

        private void stopGlowPulse() {
            for (UUID id : glowingNow) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    p.setGlowing(false);
                }
            }
            glowingNow.clear();
        }



        // TODO : A retirer après la beta
        private void logContributions10s(Claim c) {
            if (!WarManager.isTestMode()) return;

            Bukkit.getLogger().info("=== WAR " + war.getId() + " — Contributions 10s ===");
            Bukkit.getLogger().info("Claim (" + c.getGridX() + "," + c.getGridZ() + ")");

            for (UUID id : war.getAttackerPlayers()) logContributionLine(id, "ATK");
            for (UUID id : war.getDefenderPlayers()) logContributionLine(id, "DEF");

            Bukkit.getLogger().info("==========================================");
        }

        private void logContributionLine(UUID id, String side) {

            int ticks = presenceTicks.getOrDefault(id, 0);
            double presence = (ticks / 10.0) * 100.0;

            double gained = contributionTicks.getOrDefault(id, 0.0);
            int pow = war.getPlayerPower(id);

            Player p = Bukkit.getPlayer(id);
            String name = (p != null ? p.getName() : id.toString().substring(0,8));

            Bukkit.getLogger().info(
                    side + " " + name
                            + " | Power=" + pow
                            + " | +Pts=" + String.format("%.2f", gained)
                            + " | Présence=" + String.format("%.0f%%", presence)
            );
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

        public static Location getClaimCenter(Claim c) {
            if (c == null) return null;

            World world = getOverworld();
            if (world == null) return null;

            int size = ClaimManager.getActiveClaimSize();
            int cx = c.getGridX() * size + size / 2;
            int cz = c.getGridZ() * size + size / 2;
            int y  = world.getHighestBlockYAt(cx, cz);

            System.out.println("Claim center for grid (" + c.getGridX() + "," + c.getGridZ() + ") is at (" + cx + ", " + y + ", " + cz + ")");

            return new Location(world, cx + 0.5, y + 1, cz + 0.5);
        }


        void onKill(UUID killer, UUID victim) {
            kdas.computeIfAbsent(killer, k -> new KDA()).k++;
            kdas.computeIfAbsent(victim, k -> new KDA()).d++;
            if (war.getDefenderPlayers().contains(killer)) defendersKills++;
        }
        void onAssist(UUID assister) { kdas.computeIfAbsent(assister, k -> new KDA()).a++; }

        private void endRoundDefendersNoAttack() {
            this.inDetectionWindow = false;

            // Fin totale de la guerre, pas un round
            war.setStatus(WarStatus.ENDED);
            WarManager.getWars().put(war.getId(), war);

            // Nettoie visuels
            com.kingdomspvp.kingdoms.utils.ClaimVisualization.stopWarOutlines(war);

            // Message
            String defName = ChatUtil.kingdomName(war.getDefenderKingdom());
            String msg = ChatUtil.prefixWithWar(
                    org.bukkit.ChatColor.RED + "Aucune attaque n’a été lancée. " +
                            org.bukkit.ChatColor.GOLD + "Victoire des défenseurs (" + defName +
                            org.bukkit.ChatColor.GOLD + ")."
            );
            WarManager.sendToRegisteredPlayers(
                    war, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg)
            );

            for (UUID id : war.getAttackerPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    Econ.modifyBalance(p.getName(), NO_ROUND_ATK_REWARD);
                    p.sendMessage(ChatColor.GOLD + "Récompense : " + NO_ROUND_ATK_REWARD + "$");
                }
            }

            for (UUID id : war.getDefenderPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    Econ.modifyBalance(p.getName(), NO_ROUND_DEF_REWARD);
                    p.sendMessage(ChatColor.GOLD + "Récompense : " + NO_ROUND_DEF_REWARD + "$");
                }
            }

            stop(false);
        }


        // WarRuntime.java — AJOUTS (dans la classe interne WarSession)
        void attachUIIfEligible(org.bukkit.entity.Player p) {
            if (p == null || !p.isOnline()) return;
            java.util.UUID id = p.getUniqueId();
            boolean registered = war.getAttackerPlayers().contains(id) || war.getDefenderPlayers().contains(id);
            if (!registered) return;
            ui.computeIfAbsent(id, k -> BossAndBoard.attachTo(p, secondsLeft, war, targetPoints));
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
                    ClaimManager.transferClaimToKingdom(
                            attacked, true, war.getAttackerKingdom().getName()
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

            // 🔽 🔽 🔽 AJOUT ICI : TEAMS POUR LE GLOW COLORÉ 🔽 🔽 🔽

            org.bukkit.scoreboard.Team atkTeam = sb.getTeam("war_atk");
            if (atkTeam == null) {
                atkTeam = sb.registerNewTeam("war_atk");
                atkTeam.setColor(war.getAttackerKingdom().getColor()); // org.bukkit.ChatColor
            }

            org.bukkit.scoreboard.Team defTeam = sb.getTeam("war_def");
            if (defTeam == null) {
                defTeam = sb.registerNewTeam("war_def");
                defTeam.setColor(war.getDefenderKingdom().getColor());
            }

            // Ajoute tous les participants visibles sur CE scoreboard,
            // pour que leur glow prenne la bonne couleur pour ce joueur p.
            for (java.util.UUID id : war.getAttackerPlayers()) {
                Player wp = Bukkit.getPlayer(id);
                if (wp != null) {
                    atkTeam.addEntry(wp.getName()); // entry = nom du joueur
                }
            }
            for (java.util.UUID id : war.getDefenderPlayers()) {
                Player wp = Bukkit.getPlayer(id);
                if (wp != null) {
                    defTeam.addEntry(wp.getName());
                }
            }

            // 🔼 🔼 🔼 FIN AJOUT 🔼 🔼 🔼

            p.setScoreboard(sb);

            return new BossAndBoard(p, war, bar, old, sb, obj, objName, teamPrefix,
                    timer, allies, enemies, kda, points, objective);
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
