package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.listeners.TablistKingdomColors;
import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.data.KingdomsJSON;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

public class KingdomsManager {

    public static int MIN_FACTION_MEMBERS;

    private static final KingdomsJSON kingdomsJSON = new KingdomsJSON();

    public static void reloadSettings() {
        MIN_FACTION_MEMBERS = SettingsProvider.get().limits().minFactionMembers();
    }


    // Chargement des royaumes lors de l'initialisation du manager
    public static void loadKingdoms(Callback<Boolean> success) {
        kingdomsJSON.load(success);
    }

    public static List<Kingdom> getKingdoms() {
        return List.copyOf(kingdomsJSON.getAllKingdoms().values());
    }

    public static Kingdom getKingdomByName(String name) {
        return kingdomsJSON.getKingdom(name);
    }

    public static Kingdom getKingdomByFactionName(String factionName) {
        for (Kingdom kingdom : kingdomsJSON.getAllKingdoms().values()) {
            for (Faction faction : kingdom.getFactions()) { // Utilisation de la méthode getFactions()
                if (faction.getTag().equals(factionName)) {
                    return kingdom;
                }
            }
        }
        return null;
    }

    public static void addPlayerToDefaultFactionOfKingdom(Player player, Kingdom kingdom) {
        // 1) On essaie par ID

        TablistKingdomColors.refreshAll();

        Faction defaultFaction = Factions.getInstance().getFactionById(kingdom.getDefaultFactionId());
        // 2) Si introuvable, on retente par tag "Paysans_<Royaume>"
        if (defaultFaction == null) {
            String expectedTag = "Paysans_" + kingdom.getName();
            defaultFaction = Factions.getInstance().getByTag(expectedTag);
            // 3) Si toujours null, on crée la faction
            if (defaultFaction == null) {
                defaultFaction = Factions.getInstance().createFaction();
                defaultFaction.setTag(expectedTag);
                defaultFaction.setDescription(
                        ChatColor.GRAY + "Faction par défaut pour le royaume "
                                + ChatColor.GREEN + kingdom.getName()
                );
            }
            // 4) On met à jour l'ID dans le Kingdom pour les futures récupérations
            kingdom.setDefaultFactionId(defaultFaction.getId());
        }
        // 5) On affecte le joueur
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        fPlayer.setFaction(defaultFaction, true);

        player.sendMessage(
                ChatColor.GRAY + "Vous avez été ajouté à la faction par défaut du royaume "
                        + kingdom.getColor() + kingdom.getName()
        );
    }


    public static boolean playerInDefaultFaction(Player player, Kingdom kingdom) {
        Faction playerFaction = FPlayerManager.getFPlayerFaction(player);
        Faction defaultFactionKingdom = Factions.getInstance().getFactionById(kingdom.getDefaultFactionId());
        return playerFaction.getId().equals(defaultFactionKingdom.getId());
    }

    public static void addKingdom(Kingdom kingdom) {
        kingdomsJSON.addKingdom(kingdom);
    }

    public static void removeKingdom(String name) {
        kingdomsJSON.removeKingdom(name);
    }

    public static void removeFactionInKingdom(Kingdom kingdom, Faction faction) {
        kingdom.removeFaction(faction);
        saveKingdoms();
    }

    public static void addFactionInKingdom(Kingdom kingdom, Faction faction) {
        kingdom.addFaction(faction);
        saveKingdoms();
    }

    // Sauvegarde manuelle des royaumes
    public static void saveKingdoms() {
        kingdomsJSON.forceSave();
    }

    public static List<Kingdom> getAllKingdoms() {
        return List.copyOf(kingdomsJSON.getAllKingdoms().values());
    }

    public static Kingdom getKingdomOfPlayer(Player player) {
        if (player == null) return null;

        FPlayer fp = FPlayers.getInstance().getByPlayer(player);
        if (fp == null) return null;

        Faction faction = fp.getFaction();
        if (faction == null || faction.isWilderness()) return null;

        return getKingdomByFactionName(faction.getTag());
    }
}
