package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.KingdomsJSON;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.massivecraft.factions.Faction;
import org.bukkit.util.Consumer;

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

    public static void addKingdom(Kingdom kingdom) {
        kingdomsJSON.addKingdom(kingdom);
        saveKingdoms();
    }

    public static void removeKingdom(String name) {
        kingdomsJSON.removeKingdom(name);
        saveKingdoms();
    }

    // Sauvegarde manuelle des royaumes
    public static void saveKingdoms() {
        kingdomsJSON.forceSave();
    }
}
