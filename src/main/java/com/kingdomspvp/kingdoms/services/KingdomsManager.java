package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.KingdomsJSON;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

public class KingdomsManager {
    private static final KingdomsJSON kingdomsJSON = new KingdomsJSON();

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
        Faction defaultFaction = Factions.getInstance().getFactionById(kingdom.getDefaultFactionId());
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        fPlayer.setFaction(defaultFaction, true);
        player.sendMessage(ChatColor.GRAY + "Vous avez été ajouté à la faction par défaut du royaume " + kingdom.getColor() + kingdom.getName());
    }

    public static boolean playerInDefaultFaction(Player player, Kingdom kingdom) {
        Faction playerFaction = FPlayerManager.getFPlayerFaction(player);
        Faction defaultFactionKingdom = Factions.getInstance().getFactionById(kingdom.getDefaultFactionId());
        return playerFaction.getId().equals(defaultFactionKingdom.getId());
    }

    public static void addKingdom(Kingdom kingdom) {
        kingdomsJSON.addKingdom(kingdom);
        saveKingdoms();
    }

    public static void removeKingdom(String name) {
        kingdomsJSON.removeKingdom(name);
        saveKingdoms();
    }

    public static void removeFactionInKingdom(Kingdom kingdom, Faction faction) {
        kingdom.removeFaction(faction);
        saveKingdoms();
    }

    // Sauvegarde manuelle des royaumes
    public static void saveKingdoms() {
        kingdomsJSON.forceSave();
    }
}
