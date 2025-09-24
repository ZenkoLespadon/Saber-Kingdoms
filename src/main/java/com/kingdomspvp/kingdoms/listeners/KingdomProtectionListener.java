package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.block.Block;

public class KingdomProtectionListener implements Listener {

    private static final String PERM_BYPASS = "kingdoms.admin.bypass";

    public KingdomProtectionListener() {
    }

    private boolean hasBypass(Player player) {
        return player.isOp() || player.hasPermission(PERM_BYPASS);
    }

    private boolean isAllowed(Player player, Block block) {
        if (hasBypass(player)) return true;

        Claim claim = ClaimManager.getClaimByCoordinates(block.getX(), block.getZ());
        if (claim == null) return true;
        if (claim.getKingdomName().equalsIgnoreCase("None")) return true;

        Faction playerFaction = FPlayers.getInstance().getByPlayer(player).getFaction();
        String playerFactionName = playerFaction.getTag();

        return playerFactionName.equalsIgnoreCase(claim.getFactionName());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isAllowed(player, event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser de blocks dans un royaume ennemi.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isAllowed(player, event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas construire dans un royaume ennemi.");
        }
    }
}
