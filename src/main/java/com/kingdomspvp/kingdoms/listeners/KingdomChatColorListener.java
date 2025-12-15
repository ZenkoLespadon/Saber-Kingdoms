// listeners/KingdomChatColorListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class KingdomChatColorListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatFallback(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Kingdom k = KingdomsManager.getKingdomOfPlayer(p);
        ChatColor c = (k != null && k.getColor() != null) ? k.getColor() : ChatColor.WHITE;

        String line = c + p.getName() + ChatColor.RESET + ": " + e.getMessage();
        e.setCancelled(true);
        // Diffusion manuelle aux destinataires d’origine
        e.getRecipients().forEach(r -> r.sendMessage(line));
    }
}
