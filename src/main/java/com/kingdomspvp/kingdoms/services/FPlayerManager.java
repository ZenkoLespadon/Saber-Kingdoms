package com.kingdomspvp.kingdoms.services;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.struct.Role;
import org.bukkit.entity.Player;

public class FPlayerManager {

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

    public static void handleRole(Player player) {
        Role role = getFPlayerRole(player);
        if (role == Role.MODERATOR) {
            // Faire quelque chose pour les modérateurs
        }
    }
}
