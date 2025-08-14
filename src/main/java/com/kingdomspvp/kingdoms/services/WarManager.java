package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.events.WarEndEvent;
import com.kingdomspvp.kingdoms.events.WarStartEvent;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.Callback;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WarManager {

    // TODO : Stockage des guerres en JSON
    // TODO :A chaque fois qu'une nouvelle guerre est déclarée, supprimer toutes les guerres déjà passées, mais pas les guerres à venir ou en cours
    // TODO : Modifier l'incription par faction par une inscription par joueur
    // TODO : Faire l'annonce de la guerre à tous les joueurs lors de l'inscription et dire quelle faction l'a déclarée

    public static final Duration MIN_TIME_BEFORE_WAR = Duration.ofMinutes(1);
    public static final Duration MAX_TIME_BEFORE_WAR = Duration.ofMinutes(20);

    public static Duration JOIN_PROMPT_LEAD_TIME = Duration.ofSeconds(30); // passer à ofMinutes(5) à la beta

    private static final WarsJSON warsJSON = new WarsJSON();

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

    public static War declareWar(Kingdom defender, Kingdom attacker, String when) {
        // 1) Calcul du prochain ID numérique
        int nextId = warsJSON.getAllWars().keySet().stream()
                .map(idStr -> {
                    try {
                        return Integer.parseInt(idStr);
                    } catch (NumberFormatException e) {
                        return 0; // ignore les IDs qui ne sont pas numériques
                    }
                })
                .max(Integer::compareTo)
                .orElse(0)
                + 1;
        String id = String.valueOf(nextId);
        // 2) Parse de la date/heure
        LocalDateTime start = LocalDateTime.parse(when, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // 3) Création de l'objet War et persistance
        War war = new War(id, attacker, defender, start);
        warsJSON.addWar(war);
        // 4) Planification du lancement
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
        warsJSON.addWar(w); // persiste le statut
        Bukkit.getPluginManager().callEvent(new WarStartEvent(w));

        // 2ᵉ prompt au début
        sendJoinPrompt(w, "§6La guerre commence ! ");
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

        // éligible = membre d’un des deux royaumes
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
}
