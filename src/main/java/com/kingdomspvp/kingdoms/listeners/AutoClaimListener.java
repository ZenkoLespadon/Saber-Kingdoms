// listeners/AutoclaimListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.AutoClaimManager;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class AutoClaimListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!AutoClaimManager.isEnabled(p.getUniqueId())) return;
        if (!p.hasPermission("kingdoms.autoclaim.claim")) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        Claim cFrom = ClaimManager.getClaimByCoordinates(from.getBlockX(), from.getBlockZ());
        Claim cTo   = ClaimManager.getClaimByCoordinates(to.getBlockX(),   to.getBlockZ());

        if (sameClaim(cFrom, cTo)) return; // pas de changement de tuile
        if (cTo == null) return;           // hors grille
        if (!"None".equalsIgnoreCase(cTo.getKingdomName())) return; // déjà pris

        Faction f = FPlayers.getInstance().getByPlayer(p).getFaction();
        if (f == null) return;

        Kingdom k = KingdomsManager.getKingdomByFactionName(f.getTag());
        if (k == null) return;

        String kName = k.getName();

        // Doit être adjacent à un claim du royaume (aucun claim existant => ne rien faire)
        if (!ClaimManager.hasClaimForKingdom(kName)) return;
        if (!ClaimManager.isAdjacentToKingdomClaim(cTo, kName)) return;

        // Claim immédiat
        ClaimManager.transferClaimToKingdom(cTo, kName);
        p.sendMessage(ChatColor.GRAY + "Autoclaim: " + ChatColor.GREEN + "claim " + ChatColor.WHITE +
                "[" + cTo.getGridX() + "," + cTo.getGridZ() + "] " + ChatColor.GRAY + "pour " + k.getColor() + kName);
    }

    private boolean sameClaim(Claim a, Claim b) {
        if (a == null || b == null) return false;
        return a.getGridX() == b.getGridX() && a.getGridZ() == b.getGridZ();
    }
}
