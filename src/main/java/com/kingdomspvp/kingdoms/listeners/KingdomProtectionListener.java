package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.event.world.StructureGrowEvent;

public class KingdomProtectionListener implements Listener {

    private final FactionsPlugin plugin;

    public KingdomProtectionListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isAllowed(Player player, Block block) {
        int x = block.getX();
        int z = block.getZ();

        Claim claim = ClaimManager.getClaimByCoordinates(x, z);
        if (claim == null) return true;  // pas de claim = libre

        // Si le claim est libre
        if (claim.getKingdomName().equalsIgnoreCase("None")) return true;

        // Sinon, vérifie la faction du joueur
        Faction playerFaction = FPlayers.getInstance().getByPlayer(player).getFaction();
        String playerFactionName = playerFaction.getTag();

        // Si le joueur est dans la même faction = autorisé
        if (playerFactionName.equalsIgnoreCase(claim.getFactionName())) {
            return true;
        }

        // Sinon = bloqué
        return false;
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
