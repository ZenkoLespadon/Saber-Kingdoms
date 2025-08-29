package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.events.WarStartEvent;
import com.kingdomspvp.kingdoms.listeners.WarClaimListener;
import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import com.kingdomspvp.kingdoms.utils.ClaimVisualization;
import com.kingdomspvp.kingdoms.utils.WarsJSON;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gère la vie des guerres (hors phase pré-guerre : déclaration, inscriptions, délais).
 * - Planification démarrage et prompts d'inscription.
 * - Passage en INPROGRESS, détection du premier claim attaqué.
 * - Messages cliquables, téléports (via WarRuntime), persistance JSON.
 */
public class WarManager {

    // TODO : Avant la pré-alpha
    // TODO : Modifier les messages pour qu'ils prennent les couleurs des royaumes
    // TODO : Remettre toutes les constantes aux bonnes valeurs
    // TODO : Mettre un plugin PVP 1.8 (Anti-cooldown)
    // TODO : Télécharger ngrok et faire un tunnel vers le serveur local
    // TODO : Mettre une map normal
    // TODO : Set les spawn des 2 royaumes dans des endroits fermés

    public static final Duration MIN_TIME_BEFORE_WAR = Duration.ofMinutes(1);
    public static final Duration MAX_TIME_BEFORE_WAR = Duration.ofMinutes(20);

    /** Délai avant le début pour afficher le bouton d'inscription. */
    public static Duration JOIN_PROMPT_LEAD_TIME = Duration.ofSeconds(30); // ex. passez à 5 min en beta

    /** Taille de claim (en blocs), reprise du ClaimManager. */
    public static final int CLAIM_SIZE = ClaimManager.CLAIM_SIZE;

    private static final WarsJSON warsJSON = new WarsJSON();

    /** Monde de référence (claims uniquement en Overworld). */
    private static final World world = getOverworld();

    // --- Détection du premier claim attaqué (fenêtre courte en début de guerre) ---

    /** Guerres à surveiller pendant la fenêtre de détection (INPROGRESS & !combatStarted). */
    private static final Map<String, War> ACTIVE_DETECTION_WARS = new java.util.concurrent.ConcurrentHashMap<>();
    private static WarClaimListener claimListener; // null si non enregistré
    private static boolean claimListenerRegistered = false;

    /** Fenêtre max de détection avant auto-arrêt. */
    public static final Duration DETECTION_WINDOW = Duration.ofMinutes(2);
    /** Tâches de time-out par guerre. */
    private static final Map<String, Integer> detectionTimeoutTasks = new java.util.concurrent.ConcurrentHashMap<>();

    // ------------------------------------------------------------------------
    // Chargement & planification
    // ------------------------------------------------------------------------

    public static void loadWars(Callback<Boolean> success) {
        warsJSON.load(success);
        cleanupExpiredRegistrations();
        schedulePending();
        scheduleHourlyCleanup();
    }

    private static World getOverworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public static Map<String, War> getWars() {
        return warsJSON.getAllWars();
    }

    public static War getWar(String id) {
        return warsJSON.getWar(id);
    }

    public static Map<String, War> getWarsOfKingdom(Kingdom kingdom) {
        Map<String, War> warsOfKingdom = new HashMap<>();
        for (Map.Entry<String, War> entry : warsJSON.getAllWars().entrySet()) {
            War w = entry.getValue();
            if (w.getAttackerKingdom().equals(kingdom) || w.getDefenderKingdom().equals(kingdom)) {
                warsOfKingdom.put(entry.getKey(), w);
            }
        }
        return warsOfKingdom;
    }

    public static boolean hasPendingWar(Kingdom attacker, Kingdom defender) {
        return warsJSON.getAllWars().values().stream()
                .filter(w -> w.getStatus() == WarStatus.REGISTRATION || w.getStatus() == WarStatus.INPROGRESS)
                .anyMatch(w -> w.getAttackerKingdom().equals(attacker) && w.getDefenderKingdom().equals(defender));
    }

    /** Déclare une guerre et la planifie (début + prompt d'inscription). */
    public static War declareWar(Kingdom defender, Kingdom attacker, String when, List<Claim> attackableDefClaims) {
        int nextId = warsJSON.getAllWars().keySet().stream()
                .map(idStr -> { try { return Integer.parseInt(idStr); } catch (NumberFormatException e) { return 0; } })
                .max(Integer::compareTo).orElse(0) + 1;
        String id = String.valueOf(nextId);

        LocalDateTime start = LocalDateTime.parse(when, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        War war = new War(id, attacker, defender, start);
        war.setAttackableDefenderClaims(attackableDefClaims);

        warsJSON.addWar(war);
        schedule(war);
        return war;
    }

    /** Replanifie ce qui doit l'être après un redémarrage. */
    private static void schedulePending() {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, War> e : warsJSON.getAllWars().entrySet()) {
            War w = e.getValue();
            String id = e.getKey();

            if (w.getStatus() == WarStatus.REGISTRATION) {
                if (w.getStartTime().isAfter(now)) {
                    schedule(w);
                } else {
                    warsJSON.removeWar(id);
                    Bukkit.getLogger().info("[WarManager] Guerre expirée supprimée au démarrage (ID=" + id + ")");
                }
            } else if (w.getStatus() == WarStatus.INPROGRESS) {
                // Sécurité : on ne relance pas une guerre en cours au reboot
                warsJSON.removeWar(id);
                Bukkit.getLogger().info("[WarManager] Guerre en cours annulée après redémarrage (ID=" + id + ")");
            }
        }
    }

    /** Planifie le démarrage et le prompt d'inscription. */
    private static void schedule(War w) {
        long millisToStart = Duration.between(LocalDateTime.now(), w.getStartTime()).toMillis();
        if (millisToStart <= 0) {
            warsJSON.removeWar(w.getId());
            Bukkit.getLogger().info("[WarManager] Guerre expirée non planifiée (ID=" + w.getId() + ")");
            return;
        }
        long delayTicks = millisToStart / 50L;

        // Démarrage
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> startWar(w),
                delayTicks
        );

        // Prompt d'inscription
        long leadMillis = JOIN_PROMPT_LEAD_TIME.toMillis();
        long promptMillis = Math.max(0, millisToStart - leadMillis);
        long promptTicks = promptMillis / 50L;
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> sendJoinPrompt(w, ChatColor.GOLD + "La guerre contre le royaume " + ChatUtil.kingdomName(w.getDefenderKingdom()) + " commence dans 30 secondes ! "),
                promptTicks
        );
    }

    private static void sendJoinPrompt(War w, String prefix) {
        String cmd = "/k _warjoin " + w.getId();

        TextComponent msg = new TextComponent(prefix == null ? "" : prefix);
        msg.setColor(ChatColor.GOLD);

        TextComponent button = new TextComponent("[CLIQUE POUR T'INSCRIRE]");
        button.setColor(ChatColor.GREEN);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("S'inscrire à la guerre " + w.getId())));

        msg.addExtra(button);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Kingdom k = getPlayerKingdom(p);
            if (k == null) continue;
            if (k.equals(w.getAttackerKingdom()) || k.equals(w.getDefenderKingdom())) {
                p.spigot().sendMessage(msg);
            }
        }
    }

    /** Passe la guerre en INPROGRESS, lance la fenêtre de détection et le runtime. */
    private static void startWar(War w) {
        if (w.getStatus() != WarStatus.REGISTRATION) {
            Bukkit.getLogger().warning("[WarManager] startWar ignoré (ID=" + w.getId() + ", statut=" + w.getStatus() + ")");
            return;
        }

        w.setStatus(WarStatus.INPROGRESS);
        warsJSON.addWar(w);

        ClaimVisualization.startWarOutlines(w);

        ACTIVE_DETECTION_WARS.put(w.getId(), w);
        ensureClaimListenerRegistered();

        WarRuntime.onDetectionWindowStarted(w, DETECTION_WINDOW);

        long ticks = DETECTION_WINDOW.toSeconds() * 20L;
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
                FactionsPlugin.getInstance(),
                () -> disableDetectionFor(w.getId()),
                ticks
        );
        detectionTimeoutTasks.put(w.getId(), taskId);


        enableDetectionFor(w);

        sendToAttackers(w, buildWaitForAttackMessage(w.getDefenderKingdom()));

        // Un seul envoi de l'événement
        Bukkit.getPluginManager().callEvent(new WarStartEvent(w));

        // ➜ Démarrer le runtime de guerre (timer, UIs, scoring, etc.)
        WarRuntime.begin(w);
    }

    // ------------------------------------------------------------------------
    // Maintenance périodique
    // ------------------------------------------------------------------------

    public static int cleanupExpiredRegistrations() {
        Bukkit.getLogger().info("[WarManager] Nettoyage des guerres...");
        LocalDateTime now = LocalDateTime.now();

        Map<String, War> wars = getWars();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, War> entry : wars.entrySet()) {
            String id = entry.getKey();
            War w = entry.getValue();
            LocalDateTime start = w.getStartTime();

            if (w.getStatus() == WarStatus.REGISTRATION && !start.isAfter(now)) {
                toRemove.add(id);
            }
        }

        for (String id : toRemove) {
            warsJSON.removeWar(id);
            Bukkit.getLogger().info("[WarManager] Guerre supprimée (ID=" + id + ")");
        }
        return toRemove.size();
    }

    private static void scheduleHourlyCleanup() {
        long ticksHour = 20L * 60 * 60;
        Bukkit.getScheduler().runTaskTimer(
                FactionsPlugin.getInstance(),
                WarManager::cleanupExpiredRegistrations,
                ticksHour,
                ticksHour
        );
    }

    // ------------------------------------------------------------------------
    // Inscriptions
    // ------------------------------------------------------------------------

    public static boolean registerPlayerIfEligible(String warId, Player player) {
        War war = getWar(warId);
        if (war == null) return false;
        if (war.getStatus() == WarStatus.ENDED) return false;

        Kingdom k = getPlayerKingdom(player);
        if (k == null) return false;
        if (!k.equals(war.getAttackerKingdom()) && !k.equals(war.getDefenderKingdom())) return false;

        boolean added = war.addParticipant(player.getUniqueId(), k);
        if (added) {
            warsJSON.addWar(war);
        }
        return added;
    }

    private static Kingdom getPlayerKingdom(Player p) {
        FPlayer fp = FPlayers.getInstance().getByPlayer(p);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) return null;
        return KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
    }

    // ------------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------------

    public static BaseComponent[] buildParticipantsMessage(War w) {
        org.bukkit.ChatColor atkColor = w.getAttackerKingdom().getColor();
        org.bukkit.ChatColor defColor = w.getDefenderKingdom().getColor();

        List<String> attackers = uuidsToNames(w.getAttackerPlayers());
        List<String> defenders = uuidsToNames(w.getDefenderPlayers());

        String attackersLine = formatLine("Attaquants :", attackers, atkColor);
        String defendersLine = formatLine("Défenseurs :", defenders, defColor);

        String legacy = org.bukkit.ChatColor.GOLD + attackersLine + "\n" +
                org.bukkit.ChatColor.GOLD + defendersLine;

        return TextComponent.fromLegacyText(legacy);
    }

    private static List<String> uuidsToNames(Collection<UUID> uuids) {
        List<String> names = new ArrayList<>(uuids.size());
        for (UUID id : uuids) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op != null ? op.getName() : null;
            names.add(name != null ? name : id.toString().substring(0, 8));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String formatLine(String title, List<String> names, org.bukkit.ChatColor nameColor) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");
        sb.append(org.bukkit.ChatColor.GRAY).append("[");
        if (names.isEmpty()) {
            sb.append(org.bukkit.ChatColor.DARK_GRAY).append("aucun");
        } else {
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) sb.append(org.bukkit.ChatColor.GRAY).append(", ");
                sb.append(nameColor).append(names.get(i)).append(org.bukkit.ChatColor.RESET);
            }
        }
        sb.append(org.bukkit.ChatColor.GRAY).append("]");
        return sb.toString();
    }

    public static void sendMessagetoPlayersOfKingdoms(War war) {
        Kingdom attacker = war.getAttackerKingdom();
        Kingdom defender = war.getDefenderKingdom();
        String dateStr = war.getStartTime().toLocalDate().toString();
        String timeStr = war.getStartTime().toLocalTime().toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            FPlayer fpp = FPlayers.getInstance().getByPlayer(p);
            if (fpp == null || fpp.getFaction() == null || fpp.getFaction().isWilderness()) continue;
            Kingdom pk = KingdomsManager.getKingdomByFactionName(fpp.getFaction().getTag());
            if (pk == null) continue;

            if (pk.equals(attacker)) {
                p.sendMessage(
                        org.bukkit.ChatColor.GREEN + "⚔ Votre royaume attaque le royaume " + ChatUtil.kingdomName(defender)
                                + org.bukkit.ChatColor.GREEN + " le " + org.bukkit.ChatColor.AQUA + dateStr
                                + org.bukkit.ChatColor.GREEN + " à " + org.bukkit.ChatColor.AQUA + timeStr + org.bukkit.ChatColor.GREEN + "."
                );
            } else if (pk.equals(defender)) {
                p.sendMessage(
                        org.bukkit.ChatColor.RED + "⚠ Votre royaume est attaqué par le royaume " + ChatUtil.kingdomName(attacker)
                                + org.bukkit.ChatColor.RED + " le " + org.bukkit.ChatColor.AQUA + dateStr
                                + org.bukkit.ChatColor.RED + " à " + org.bukkit.ChatColor.AQUA + timeStr + org.bukkit.ChatColor.RED + "."
                );
            }

        }
    }

    public static void sendToRegisteredPlayers(War w, BaseComponent[] comps) {
        for (UUID id : w.getAttackerPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
        for (UUID id : w.getDefenderPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
    }

    public static BaseComponent[] buildWaitForAttackMessage(Kingdom defender) {
        String defName = ChatUtil.kingdomName(defender);
        return TextComponent.fromLegacyText(ChatColor.GOLD + "Attaquez un claim du royaume " + defName + ChatColor.GOLD + " pour démarrer la guerre !");
    }

    public static BaseComponent[] buildTpToWarNowMessage(String warId) {
        TextComponent btn = new TextComponent(ChatColor.GREEN + "[Cliquez ici pour vous téléporter à la guerre]");
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/k _tp_to_war " + warId));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Rejoindre la guerre " + warId)));
        return new BaseComponent[]{ btn };
    }

    public static void sendToAttackers(War w, BaseComponent[] comps) {
        for (UUID id : w.getAttackerPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
    }

    public static void sendToDefenders(War w, BaseComponent[] comps) {
        for (UUID id : w.getDefenderPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
    }

    public static void sendToAttackers(War w, String legacy) {
        sendToAttackers(w, TextComponent.fromLegacyText(legacy));
    }

    public static void sendToDefenders(War w, String legacy) {
        sendToDefenders(w, TextComponent.fromLegacyText(legacy));
    }

    // ------------------------------------------------------------------------
    // Détection du premier claim attaqué
    // ------------------------------------------------------------------------

    private static void ensureClaimListenerRegistered() {
        if (!claimListenerRegistered) {
            claimListener = new WarClaimListener(ACTIVE_DETECTION_WARS);
            Bukkit.getPluginManager().registerEvents(claimListener, FactionsPlugin.getInstance());
            claimListenerRegistered = true;
        }
    }

    private static void unregisterClaimListenerIfIdle() {
        if (claimListenerRegistered && ACTIVE_DETECTION_WARS.isEmpty()) {
            // Désinscription ciblée du listener de ce plugin
            HandlerList.unregisterAll(claimListener);
            claimListenerRegistered = false;
            claimListener = null;
        }
    }

    /** À appeler quand une guerre passe à INPROGRESS. */
    private static void enableDetectionFor(War w) {
        ACTIVE_DETECTION_WARS.put(w.getId(), w);
        ensureClaimListenerRegistered();

        long ticks = DETECTION_WINDOW.toSeconds() * 20L;
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
                FactionsPlugin.getInstance(),
                () -> disableDetectionFor(w.getId()),
                ticks
        );
        detectionTimeoutTasks.put(w.getId(), taskId);
    }

    private static void disableDetectionFor(String warId) {
        ACTIVE_DETECTION_WARS.remove(warId);

        Integer tid = detectionTimeoutTasks.remove(warId);
        if (tid != null) {
            Bukkit.getScheduler().cancelTask(tid);
        }
        unregisterClaimListenerIfIdle();

        War w = getWar(warId);
        if (w != null) {
            // Stoppe l'affichage "détection" côté runtime (idempotent)
            WarRuntime.onDetectionWindowEnded(w);

            // Aucune attaque n'a démarré avant la fin de la fenêtre → victoire DEF
            if (!w.hasCombatStarted() && w.getStatus() == WarStatus.INPROGRESS) {
                WarRuntime.defendersAutoWinNoAttack(w);
            }
        }
    }




    /** Appelé par le listener quand le premier claim défenseur est engagé par un attaquant. */
    public static void handleFirstClaimAttacked(War w, Claim to) {
        if (w.hasCombatStarted()) return;
        if (!w.isAttackable(to.getGridX(), to.getGridZ())) return;

        w.markCombatStarted(to.getGridX(), to.getGridZ());
        warsJSON.addWar(w);
        disableDetectionFor(w.getId());
        WarRuntime.onDetectionWindowEnded(w);

        sendWarStartMessage(w);

        sendToRegisteredPlayers(w, buildTpToWarNowMessage(w.getId()));
        ClaimVisualization.switchToAttackedClaimOnly(w, to);

        // ➜ Informe le runtime pour qu'il connaisse la zone d'affrontement
        WarRuntime.onFirstClaimAttacked(w, to);

        Bukkit.getLogger().info("Premier claim attaqué pour la guerre " + w.getId()
                + " dans le royaume " + to.getKingdomName()
                + " (grid : " + to.getGridX() + "," + to.getGridZ() + ")");
    }

    // ------------------------------------------------------------------------
    // Utilitaires de claims / positions
    // ------------------------------------------------------------------------

    /** Claim actuellement attaqué (selon coordonnées stockées dans War). */
    public static Claim getAttackedClaim(War w) {
        Integer gx = w.getAttackedGridX();
        Integer gz = w.getAttackedGridZ();
        if (gx == null || gz == null) return null;
        int centerX = gx * CLAIM_SIZE + CLAIM_SIZE / 2;
        int centerZ = gz * CLAIM_SIZE + CLAIM_SIZE / 2;
        return ClaimManager.getClaimByCoordinates(centerX, centerZ);
    }

    /** Centre d’un claim en Overworld. */
    public static Location getClaimCenter(Claim c) {
        int cx = c.getGridX() * CLAIM_SIZE + CLAIM_SIZE / 2;
        int cz = c.getGridZ() * CLAIM_SIZE + CLAIM_SIZE / 2;
        int y = world.getHighestBlockYAt(cx, cz);
        return new Location(world, cx + 0.5, y + 1, cz + 0.5);
    }

    /** Cherche un claim adjacent (N/S/E/O) appartenant au royaume attaquant. */
    public static Claim findAdjacentAttackerClaim(War w, Claim attacked) {
        String attackerKingdomName = w.getAttackerKingdom().getName();
        int gx = attacked.getGridX();
        int gz = attacked.getGridZ();

        int[][] dirs = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        for (int[] d : dirs) {
            int ngx = gx + d[0], ngz = gz + d[1];
            int blockX = ngx * CLAIM_SIZE + CLAIM_SIZE / 2;
            int blockZ = ngz * CLAIM_SIZE + CLAIM_SIZE / 2;
            Claim neighbor = ClaimManager.getClaimByCoordinates(blockX, blockZ);
            if (neighbor != null && attackerKingdomName.equalsIgnoreCase(neighbor.getKingdomName())) {
                return neighbor;
            }
        }
        return null;
    }

    /**
     * Point de ralliement à 'offset' blocs à l’intérieur du claim 'staging' (attaquant), proche de la frontière avec 'attacked'.
     */
    public static Location getStagingPointNearBorder(Claim staging, Claim attacked, int offset) {
        int sx = staging.getGridX(), sz = staging.getGridZ();
        int ax = attacked.getGridX(), az = attacked.getGridZ();

        if (sx == ax && sz == az + 1) {
            // staging au S du attacked → bord nord de staging
            int x = sx * CLAIM_SIZE + CLAIM_SIZE / 2;
            int z = sz * CLAIM_SIZE + offset;
            int y = world.getHighestBlockYAt(x, z);
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        } else if (sx == ax && sz == az - 1) {
            // staging au N du attacked → bord sud de staging
            int x = sx * CLAIM_SIZE + CLAIM_SIZE / 2;
            int z = (sz + 1) * CLAIM_SIZE - 1 - offset;
            int y = world.getHighestBlockYAt(x, z);
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        } else if (sz == az && sx == ax + 1) {
            // staging à l'E du attacked → bord ouest de staging
            int x = sx * CLAIM_SIZE + offset;
            int z = sz * CLAIM_SIZE + CLAIM_SIZE / 2;
            int y = world.getHighestBlockYAt(x, z);
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        } else if (sz == az && sx == ax - 1) {
            // staging à l'O du attacked → bord est de staging
            int x = (sx + 1) * CLAIM_SIZE - 1 - offset;
            int z = sz * CLAIM_SIZE + CLAIM_SIZE / 2;
            int y = world.getHighestBlockYAt(x, z);
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }

        // Non adjacent → centre du staging
        return getClaimCenter(staging);
    }

    // WarManager.java
    public static void enableDetectionForNextRound(War w) {
        // réutilise la même mécanique que le 1er round
        ACTIVE_DETECTION_WARS.put(w.getId(), w);
        ensureClaimListenerRegistered();

        WarRuntime.onDetectionWindowStarted(w, DETECTION_WINDOW);

        long ticks = DETECTION_WINDOW.toSeconds() * 20L;
        int taskId = org.bukkit.Bukkit.getScheduler().scheduleSyncDelayedTask(
                FactionsPlugin.getInstance(),
                () -> disableDetectionFor(w.getId()),
                ticks
        );
        detectionTimeoutTasks.put(w.getId(), taskId);

        // Message d’instruction aux attaquants (facultatif)
        sendToAttackers(w, buildWaitForAttackMessage(w.getDefenderKingdom()));
    }

    // Java
    public static void prepareNextRound(War w, boolean nextRoundWillStart) {
        if (w == null) return;

        // A) Stop tout affichage restant
        ClaimVisualization.stopWarOutlines(w);

        // B) Reset la phase de combat
        w.resetRound();

        // C) Recalcule la frontière attaquable côté DEF
        java.util.List<Claim> attackables = ClaimManager.getDefenderClaimsAdjacentToAttacker(
                w.getDefenderKingdom().getName(),
                w.getAttackerKingdom().getName()
        );
        w.setAttackableDefenderClaims(attackables);

        // D) Persiste
        warsJSON.addWar(w);

        if (nextRoundWillStart) {
            // E) Relance l’affichage: uniquement les claims attaquables
            ClaimVisualization.startWarOutlines(w);

            // F) Réarmer la détection “premier claim attaqué”
            ACTIVE_DETECTION_WARS.put(w.getId(), w);

            WarRuntime.onDetectionWindowStarted(w, DETECTION_WINDOW);

            ensureClaimListenerRegistered();
            long ticks = DETECTION_WINDOW.toSeconds() * 20L;
            int taskId = org.bukkit.Bukkit.getScheduler().scheduleSyncDelayedTask(
                    FactionsPlugin.getInstance(),
                    () -> disableDetectionFor(w.getId()),
                    ticks
            );
            detectionTimeoutTasks.put(w.getId(), taskId);

            // G) Feedback
            sendToAttackers(w, buildWaitForAttackMessage(w.getDefenderKingdom()));
        }
    }

    // Java
    public static void sendWarStartMessage(War war) {
        String defName = ChatUtil.kingdomName(war.getDefenderKingdom());
        String msg = org.bukkit.ChatColor.GOLD + "La guerre contre le Royaume " + defName + org.bukkit.ChatColor.GOLD + " commence !";
        sendToRegisteredPlayers(war, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
    }

}
