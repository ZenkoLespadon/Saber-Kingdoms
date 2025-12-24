package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Collection;

public final class WarBlockEditListener implements Listener {

    static long nbTicksPerSecond = 20L;
    static long nbSeconds = 20L;

    private static final long REVERT_AFTER_TICKS = nbTicksPerSecond * nbSeconds;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isInActiveWarZone(b)) return;

        // Force l’autorisation dans la zone de guerre
        e.setCancelled(false);

        // Snapshot AVANT casse
        BlockState before = b.getState();

        // Revenir à l’état précédent dans 20 s
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> safeRestore(before),
                REVERT_AFTER_TICKS
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!isInActiveWarZone(b)) return;

        // Interdire la pose de TNT et de feu
        if (b.getType() == Material.TNT || b.getType() == Material.FIRE) {
            e.setCancelled(true);
            return;
        }

        // Force l’autorisation dans la zone de guerre
        e.setCancelled(false);

        // Snapshot AVANT placement (le bloc qui va être remplacé)
        BlockState before = e.getBlockReplacedState();

        // Revenir à l’état précédent dans 20 s
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> safeRestore(before),
                REVERT_AFTER_TICKS
        );
    }

    /** Restaure prudemment l’état sauvegardé (ignore les erreurs). */
    private void safeRestore(BlockState state) {
        try {
            if (state != null && state.getWorld().isChunkLoaded(state.getX() >> 4, state.getZ() >> 4)) {
                state.update(true, false); // true = forces block data, false = ne déclenche pas de physique
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Vrai si le block est dans le claim actuellement attaqué
     * d’une guerre en cours et dont le combat a démarré.
     */
    private boolean isInActiveWarZone(Block b) {
        Location loc = b.getLocation();
        Claim here = ClaimManager.getClaimByCoordinates(loc.getBlockX(), loc.getBlockZ());
        if (here == null) return false;

        Collection<War> wars = WarManager.getWars().values();
        for (War w : wars) {
            if (w.getStatus() != WarStatus.INPROGRESS || !w.hasCombatStarted()) continue;

            Claim attacked = WarManager.getAttackedClaim(w);
            if (attacked == null) continue;

            if (attacked.getGridX() == here.getGridX() && attacked.getGridZ() == here.getGridZ()) {
                return true;
            }
        }
        return false;
    }
}