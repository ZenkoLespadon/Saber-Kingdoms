package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.massivecraft.factions.Faction;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class KingdomsManager {
    private List<Kingdom> kingdoms;

    public KingdomsManager() {
        kingdoms = new ArrayList<>();
        createDefaultKingdoms();
    }

    private void createDefaultKingdoms() {
        kingdoms.add(new Kingdom("Bleu", ChatColor.BLUE));
        kingdoms.add(new Kingdom("Rouge", ChatColor.RED));
        kingdoms.add(new Kingdom("Jaune", ChatColor.YELLOW));
        kingdoms.add(new Kingdom("Vert", ChatColor.GREEN));
    }

    public List<Kingdom> getKingdoms() {
        return kingdoms;
    }

    public Kingdom getKingdomByName(String name) {
        for (Kingdom kingdom : kingdoms) {
            if (kingdom.getName().equalsIgnoreCase(name)) {
                return kingdom;
            }
        }
        return null;
    }

    public Kingdom getKingdomByFactionName(String factionName) {
        for (Kingdom kingdom : kingdoms) {
            for (Faction faction : kingdom.getFactions()) {
                if (faction.getTag().equalsIgnoreCase(factionName)) {
                    return kingdom;
                }
            }
        }
        return null;
    }
}
