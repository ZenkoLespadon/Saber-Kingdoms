package com.kingdomspvp.kingdoms.utils;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.struct.Role;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class PlayerUtil {

    private PlayerUtil() {}

    public static boolean isInOverworld(Player player) {
        boolean inOverworld = player.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL;

        return inOverworld;
    }

    public static boolean isFacLeaderWithMinMembers(Kingdom kingdom, FPlayer fplayer, Player sender) {
        boolean inDefaultFaction = KingdomsManager.playerInDefaultFaction(sender, kingdom);
        boolean leaderOk  = fplayer.getRole().isAtLeast(Role.COLEADER);
        boolean sizeOk    = fplayer.getFaction().getFPlayers().size() >= KingdomsManager.MIN_FACTION_MEMBERS;

        if (!leaderOk || !sizeOk || inDefaultFaction) {
            if (sender != null) {
                sender.sendMessage(
                        ChatColor.RED + "Vous devez être le chef d'une faction d'au moins "
                                + KingdomsManager.MIN_FACTION_MEMBERS
                                + " membres pour éxecuter cette commande."
                );
            }
            return false;
        }
        return true;
    }

    public static boolean isFacLeaderWithMinMembers(FPlayer fplayer, Player sender) {
        Kingdom kingdom = KingdomsManager.getKingdomByFactionName(fplayer.getFaction().getTag());
        return isFacLeaderWithMinMembers(kingdom, fplayer, sender);
    }
}
