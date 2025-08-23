package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.events.WarEndEvent;
import com.kingdomspvp.kingdoms.events.WarStartEvent;
import com.kingdomspvp.kingdoms.listeners.WarClaimListener;
import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.Callback;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class WarManager {

    // TODO : Affichage uniquemet des claims attaques avec les particules
    // TODO : TP des gens inscrits à l'endroit du claim attaqué
    // TODO : Faire l'annonce de la guerre à tous les joueurs lors de l'inscription et dire quelle faction l'a déclarée

    public static final Duration MIN_TIME_BEFORE_WAR = Duration.ofMinutes(1);
    public static final Duration MAX_TIME_BEFORE_WAR = Duration.ofMinutes(20);

    public static Duration JOIN_PROMPT_LEAD_TIME = Duration.ofSeconds(30);// passer à ofMinutes(5) à la beta

    private static final WarsJSON warsJSON = new WarsJSON();

    // Guerres à surveiller pendant la fenêtre de détection (INPROGRESS & !combatStarted)
    private static final Map<String, War> ACTIVE_DETECTION_WARS = new java.util.concurrent.ConcurrentHashMap<>();
    private static WarClaimListener claimListener; // null si non enregistré
    private static boolean claimListenerRegistered = false;

    // Fenêtre max de détection avant auto-arrêt (optionnel)
    public static final java.time.Duration DETECTION_WINDOW = java.time.Duration.ofMinutes(2);
    // Tâches de time-out par guerre
    private static final Map<String, Integer> detectionTimeoutTasks = new java.util.concurrent.ConcurrentHashMap<>();

    public static void loadWars(Callback<Boolean> success) {
        warsJSON.load(success);
        cleanupExpiredRegistrations();
        schedulePending();
        scheduleHourlyCleanup();
    }


    public static Map<String, War> getWars() {
        return warsJSON.getAllWars();
    }

    public static War getWar(String id) {
        return warsJSON.getWar(id);
    }

    public static Map<String, War> getWarsOfKingdom(Kingdom kingdom) {
        Map<String, War> warsOfKingdom = new HashMap<>();
        // itère sur toutes les guerres stockées
        for (Map.Entry<String, War> entry : warsJSON.getAllWars().entrySet()) {
            War w = entry.getValue();
            // si le royaume est attaquant ou défenseur, on l'ajoute
            if (w.getAttackerKingdom().equals(kingdom)
                    || w.getDefenderKingdom().equals(kingdom)) {
                warsOfKingdom.put(entry.getKey(), w);
            }
        }
        return warsOfKingdom;
    }

    public static boolean hasPendingWar(Kingdom attacker, Kingdom defender) {
        return warsJSON.getAllWars().values().stream()
                .filter(w -> w.getStatus() == WarStatus.REGISTRATION || w.getStatus() == WarStatus.INPROGRESS)
                .anyMatch(w ->
                        w.getAttackerKingdom().equals(attacker)
                                && w.getDefenderKingdom().equals(defender)
                );
    }

    // services/WARManager.java
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
                warsJSON.removeWar(id);
                Bukkit.getLogger().info("[WarManager] Guerre en cours annulée après redémarrage (ID=" + id + ")");
            }
        }
    }


    private static void schedule(War w) {
        long millisToStart = Duration.between(LocalDateTime.now(), w.getStartTime()).toMillis();
        if (millisToStart <= 0) {
            warsJSON.removeWar(w.getId());
            Bukkit.getLogger().info("[WarManager] Guerre expirée non planifiée (ID=" + w.getId() + ")");
            return;
        }
        long delayTicks = millisToStart / 50L;

        // planifie le start
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> startWar(w),
                delayTicks
        );

        // planifie le prompt “pré‑guerre”
        long leadMillis = JOIN_PROMPT_LEAD_TIME.toMillis();
        long promptMillis = Math.max(0, millisToStart - leadMillis);
        long promptTicks = promptMillis / 50L;
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> sendJoinPrompt(w, /*prefix*/ "§6La guerre va commencer bientôt ! "),
                promptTicks
        );
    }

    private static void sendJoinPrompt(War w, String prefix) {
        String cmd = "/k _warjoin " + w.getId();

        // Préfixe
        TextComponent msg = new TextComponent(prefix == null ? "" : prefix);
        msg.setColor(ChatColor.GOLD);

        // Bouton cliquable
        TextComponent button = new TextComponent("[CLIQUE POUR T'INSCRIRE]");
        button.setColor(ChatColor.GREEN);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("S'inscrire à la guerre " + w.getId())));

        // Assembler préfixe + bouton
        msg.addExtra(button);

        // Envoi aux joueurs éligibles
        for (Player p : Bukkit.getOnlinePlayers()) {
            Kingdom k = getPlayerKingdom(p);
            if (k == null) continue;
            if (k.equals(w.getAttackerKingdom()) || k.equals(w.getDefenderKingdom())) {
                p.spigot().sendMessage(msg); // ✅ Envoi avec API Bungee
            }
        }
    }

    private static void startWar(War w) {
        if (w.getStatus() != WarStatus.REGISTRATION) {
            Bukkit.getLogger().warning("[WarManager] startWar ignoré (ID=" + w.getId() + ", statut=" + w.getStatus() + ")");
            return;
        }

        w.setStatus(WarStatus.INPROGRESS);
        warsJSON.addWar(w);

        ClaimVisualization.startWarOutlines(w);
        enableDetectionFor(w);
        sendToAttackers(w, buildWaitForAttackMessage());
        org.bukkit.Bukkit.getPluginManager().callEvent(new WarStartEvent(w));
        Bukkit.getPluginManager().callEvent(new WarStartEvent(w));
    }

    public static int cleanupExpiredRegistrations() {
        Bukkit.getLogger().info("[WarManager] Nettoyage des guerres...");
        LocalDateTime now = LocalDateTime.now();

        Map<String, War> wars = getWars();
        Bukkit.getLogger().info("[WarManager] " + wars.size() + " guerre(s) à analyser.");

        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, War> entry : wars.entrySet()) {
            String id = entry.getKey();
            War w = entry.getValue();
            LocalDateTime start = w.getStartTime();

            Bukkit.getLogger().info("[WarManager] Test guerre ID=" + id
                    + " | Statut=" + w.getStatus()
                    + " | Début=" + start
                    + " | Maintenant=" + now);

            if (w.getStatus() == WarStatus.REGISTRATION && !start.isAfter(now)) {
                Bukkit.getLogger().info("[WarManager]  -> Marquée pour suppression (expirée)");
                toRemove.add(id);
            } else {
                Bukkit.getLogger().info("[WarManager]  -> Conservée");
            }
        }

        for (String id : toRemove) {
            warsJSON.removeWar(id);
            Bukkit.getLogger().info("[WarManager] Guerre supprimée (ID=" + id + ")");
        }
        if (toRemove.isEmpty()) {
            Bukkit.getLogger().info("[WarManager] Aucune guerre expirée supprimée.");
        } else {
            Bukkit.getLogger().info("[WarManager] " + toRemove.size() + " guerre(s) expirée(s) supprimée(s).");
        }

        return toRemove.size();
    }

    private static void scheduleHourlyCleanup() {
        long ticksHour = 20L * 60 * 60; // 20 ticks * 60 secondes * 60 minutes = 1 heure
        Bukkit.getScheduler().runTaskTimer(
                FactionsPlugin.getInstance(),
                WarManager::cleanupExpiredRegistrations,
                ticksHour,   // délai avant le premier run (1 h)
                ticksHour    // intervalle entre deux exécutions (1 h)
        );
    }

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

    public static BaseComponent[] buildParticipantsMessage(War w) {
        // Couleurs de royaumes (Bukkit ChatColor -> legacy § codes, OK pour fromLegacyText)
        org.bukkit.ChatColor atkColor = w.getAttackerKingdom().getColor();
        org.bukkit.ChatColor defColor = w.getDefenderKingdom().getColor();

        // Noms
        java.util.List<String> attackers = uuidsToNames(w.getAttackerPlayers());
        java.util.List<String> defenders = uuidsToNames(w.getDefenderPlayers());

        String attackersLine = formatLine("Attaquants :", attackers, atkColor);
        String defendersLine = formatLine("Défenseurs :", defenders, defColor);

        String legacy =
                org.bukkit.ChatColor.GOLD + attackersLine + "\n" +
                        org.bukkit.ChatColor.GOLD + defendersLine;

        return TextComponent.fromLegacyText(legacy);
    }

    private static java.util.List<String> uuidsToNames(java.util.Collection<java.util.UUID> uuids) {
        java.util.List<String> names = new java.util.ArrayList<>(uuids.size());
        for (java.util.UUID id : uuids) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op != null ? op.getName() : null;
            names.add(name != null ? name : id.toString().substring(0, 8));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String formatLine(String title, java.util.List<String> names, org.bukkit.ChatColor nameColor) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");
        sb.append(org.bukkit.ChatColor.GRAY).append("[");
        if (names.isEmpty()) {
            sb.append(org.bukkit.ChatColor.DARK_GRAY).append("aucun");
        } else {
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) sb.append(org.bukkit.ChatColor.GRAY).append(", ");
                sb.append(nameColor).append(names.get(i))
                        .append(org.bukkit.ChatColor.RESET);
            }
        }
        sb.append(org.bukkit.ChatColor.GRAY).append("]");
        return sb.toString();
    }

    public static void sendMessagetoPlayersOfKingdoms(War war) {
        Kingdom attacker = war.getAttackerKingdom();
        Kingdom defender = war.getDefenderKingdom();
        String dateStr = war.getStartTime().toLocalDate().toString(); // YYYY-MM-DD
        String timeStr = war.getStartTime().toLocalTime().toString(); // HH:MM
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            // Récup royaume du joueur en toute sécurité
            com.massivecraft.factions.FPlayer fpp = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(p);
            if (fpp == null || fpp.getFaction() == null || fpp.getFaction().isWilderness()) continue;
            Kingdom pk = com.kingdomspvp.kingdoms.services.KingdomsManager.getKingdomByFactionName(fpp.getFaction().getTag());
            if (pk == null) continue;

            if (pk.equals(attacker)) {
                // Joueurs attaquants
                p.sendMessage(
                        org.bukkit.ChatColor.GREEN + "⚔ Votre royaume attaque le royaume " + defender.getColor() + defender.getName()
                                + org.bukkit.ChatColor.GREEN + " le " + org.bukkit.ChatColor.AQUA + dateStr
                                + org.bukkit.ChatColor.GREEN + " à " + org.bukkit.ChatColor.AQUA + timeStr + org.bukkit.ChatColor.GREEN + "."
                );
            } else if (pk.equals(defender)) {
                // Joueurs défenseurs
                p.sendMessage(
                        org.bukkit.ChatColor.RED + "⚠ Votre royaume est attaqué par le royaume " + attacker.getColor() + attacker.getName()
                                + org.bukkit.ChatColor.RED + " le " + org.bukkit.ChatColor.AQUA + dateStr
                                + org.bukkit.ChatColor.RED + " à " + org.bukkit.ChatColor.AQUA + timeStr + org.bukkit.ChatColor.RED + "."
                );
            }
        }
    }

    private static void ensureClaimListenerRegistered() {
        if (!claimListenerRegistered) {
            claimListener = new WarClaimListener(ACTIVE_DETECTION_WARS);
            org.bukkit.Bukkit.getPluginManager().registerEvents(claimListener, FactionsPlugin.getInstance());
            claimListenerRegistered = true;
        }
    }

    private static void unregisterClaimListenerIfIdle() {
        if (claimListenerRegistered && ACTIVE_DETECTION_WARS.isEmpty()) {
            HandlerList.unregisterAll();
            claimListenerRegistered = false;
            claimListener = null;
        }
    }

    // À appeler quand une guerre passe à INPROGRESS
    private static void enableDetectionFor(War w) {
        ACTIVE_DETECTION_WARS.put(w.getId(), w);
        ensureClaimListenerRegistered();

        // planifier un arrêt automatique de la détection pour cette guerre
        long ticks = DETECTION_WINDOW.toSeconds() * 20L;
        int taskId = org.bukkit.Bukkit.getScheduler().scheduleSyncDelayedTask(
                FactionsPlugin.getInstance(),
                () -> disableDetectionFor(w.getId()), // si rien ne s'est passé
                ticks
        );
        detectionTimeoutTasks.put(w.getId(), taskId);
    }

    // À appeler quand un claim est attaqué OU quand la fenêtre expire
    private static void disableDetectionFor(String warId) {
        ACTIVE_DETECTION_WARS.remove(warId);

        Integer tid = detectionTimeoutTasks.remove(warId);
        if (tid != null) {
            org.bukkit.Bukkit.getScheduler().cancelTask(tid);
        }
        unregisterClaimListenerIfIdle();
    }

    // services/WarManager.java
    public static void handleFirstClaimAttacked(War w, Claim to) {
        if (w.hasCombatStarted()) return;
        // ➜ sécurité supplémentaire
        if (!w.isAttackable(to.getGridX(), to.getGridZ())) return;

        w.markCombatStarted(to.getGridX(), to.getGridZ());
        warsJSON.addWar(w);
        disableDetectionFor(w.getId());
        sendToRegisteredPlayers(w, buildJoinWarNowMessage(w.getId()));
        ClaimVisualization.switchToAttackedClaimOnly(w, to);
        System.out.println("Premier claim attaqué pour la guerre " + w.getId()
                + " dans le royaume " + to.getKingdomName()
                + " (coordonnées : " + to.getGridX() + ", " + to.getGridZ() + ")");
    }

    public static void sendToRegisteredPlayers(War w, BaseComponent[] comps) {

        System.out.println("message send");
        // attaquants
        for (java.util.UUID id : w.getAttackerPlayers()) {
            var p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
        // défenseurs
        for (java.util.UUID id : w.getDefenderPlayers()) {
            var p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
    }

    // Message “attendez le début”
    public static BaseComponent[] buildWaitForAttackMessage() {
        return TextComponent.fromLegacyText(
                ChatColor.GOLD + "Attaquez un claim pour commencer la guerre"
        );
    }

    // Message “rejoindre la guerre” (cliquable)
    public static BaseComponent[] buildJoinWarNowMessage(String warId) {
        TextComponent root = new TextComponent(ChatColor.GOLD + "La guerre commence, ");
        TextComponent btn  = new TextComponent(ChatColor.GREEN + "[cliquez ici pour rejoindre la guerre]");
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/k _tp_to_war " + warId));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Rejoindre la guerre " + warId)));
        root.addExtra(btn);
        return new BaseComponent[]{ root };
    }

    // services/WarManager.java
    public static void sendToAttackers(War w, BaseComponent[] comps) {
        for (java.util.UUID id : w.getAttackerPlayers()) {
            var p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
    }

    public static void sendToDefenders(War w, BaseComponent[] comps) {
        for (java.util.UUID id : w.getDefenderPlayers()) {
            var p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.spigot().sendMessage(comps);
        }
    }

    // (Optionnel) surcharges pratiques en texte legacy
    public static void sendToAttackers(War w, String legacy) {
        sendToAttackers(w, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(legacy));
    }

    public static void sendToDefenders(War w, String legacy) {
        sendToDefenders(w, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(legacy));
    }
}
