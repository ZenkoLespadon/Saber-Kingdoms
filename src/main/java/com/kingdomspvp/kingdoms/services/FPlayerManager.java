package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.struct.Role;
import org.bukkit.entity.Player;

public class FPlayerManager {

    //TODO : Bouger les méthodes qui n'ont rien à faire là dans KingdomsManager
    public static FPlayer getFPlayer(Player player) {
        return FPlayers.getInstance().getByPlayer(player);
    }

    public static Role getFPlayerRole(Player player) {
        FPlayer fPlayer = getFPlayer(player);
        return fPlayer.getRole();
    }

    public static Faction getFPlayerFaction(Player player) {
        FPlayer fPlayer = getFPlayer(player);
        return fPlayer.getFaction();
    }

    public static Kingdom getPlayerKingdom(Player player) {
        Faction faction = getFPlayerFaction(player);
        return KingdomsManager.getKingdomByFactionName(faction.getTag());
    }

    public static void handleRole(Player player) {
        Role role = getFPlayerRole(player);
        if (role == Role.MODERATOR) {
            // Faire quelque chose pour les modérateurs
        }
    }
}
