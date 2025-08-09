package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.events.WarEndEvent;
import com.kingdomspvp.kingdoms.events.WarStartEvent;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.WarsJSON;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WarManager {

    // TODO : Stockage des guerres en JSON
    // TODO :A chaque fois qu'une nouvelle guerre est déclarée, supprimer toutes les guerres déjà passées, mais pas les guerres à venir ou en cours
    // TODO : Modifier l'incription par faction par une inscription par joueur
    // TODO : Faire l'annonce de la guerre à tous les joueurs lors de l'inscription et dire quelle faction l'a déclarée

    public static final Duration MIN_TIME_BEFORE_WAR = Duration.ofMinutes(2);
    public static final Duration MAX_TIME_BEFORE_WAR = Duration.ofMinutes(20);

    private static final WarsJSON warsJSON = new WarsJSON();

    public static void loadWars(Callback<Boolean> success) {
        warsJSON.load(success);
        cleanupExpiredRegistrations(); // Supprime ce qui est expiré
        schedulePending();             // Planifie le reste ou annule si redémarrage
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
                    // Guerre à venir → planifier
                    schedule(w);
                } else {
                    // Inscription expirée → suppression
                    warsJSON.removeWar(id);
                    Bukkit.getLogger().info("[WarManager] Guerre expirée supprimée au démarrage (ID=" + id + ")");
                }
            }
            else if (w.getStatus() == WarStatus.INPROGRESS) {
                // Si le serveur redémarre, on annule directement la guerre
                warsJSON.removeWar(id);
                Bukkit.getLogger().info("[WarManager] Guerre en cours annulée après redémarrage (ID=" + id + ")");
            }
        }
    }


    private static void schedule(War w) {
        long millis = Duration.between(LocalDateTime.now(), w.getStartTime()).toMillis();
        if (millis <= 0) {
            // L’heure est déjà passée : on traite comme expirée (cohérent avec cleanup)
            warsJSON.removeWar(w.getId());
            Bukkit.getLogger().info("[WarManager] Guerre expirée non planifiée (ID=" + w.getId() + ")");
            return;
        }
        long delayTicks = millis / 50L;
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> startWar(w),
                delayTicks
        );
    }

    private static void startWar(War w) {
        // Garde-fous : on ne passe à INPROGRESS que depuis REGISTRATION
        if (w.getStatus() != WarStatus.REGISTRATION) {
            Bukkit.getLogger().warning("[WarManager] startWar ignoré pour ID=" + w.getId()
                    + " car statut=" + w.getStatus());
            return;
        }
        w.setStatus(WarStatus.INPROGRESS);
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
}
