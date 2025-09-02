// src/main/java/com/kingdomspvp/kingdoms/listeners/WarDeathListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.WarManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

// TODO : Fusionner la branche war avec master

public class WarDeathListener implements Listener {
    // Java
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Bukkit.getLogger().info("Player died");

        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        War war = getWarOfPlayer(playerId);
        if (war == null || war.getStatus() != com.kingdomspvp.kingdoms.model.WarStatus.INPROGRESS) return;

        Bukkit.getLogger().info("Player " + player.getName() + " is dead in war " + war.getId() + ", scheduling tp message");

        Bukkit.getScheduler().runTaskLater(
                com.massivecraft.factions.FactionsPlugin.getInstance(),
                () -> sendTpToWarMessage(playerId, war),
                20L * 15 // 30 secondes
        );
    }

    private void sendTpToWarMessage(UUID playerId, War war) {
        Bukkit.getLogger().info("Sending tp to war message to player " + playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && war.getStatus() == WarStatus.INPROGRESS) {
            player.spigot().sendMessage(WarManager.buildTpToWarNowMessage(war.getId()));
        }
    }

    private War getWarOfPlayer(UUID playerId) {
        Bukkit.getLogger().info("Checking wars for player " + playerId);
        for (War war : WarManager.getWars().values()) {
            Bukkit.getLogger().info("Checking war " + war.getId());
            if (war.getStatus() == WarStatus.ENDED) continue;
            if (war.getAttackerPlayers().contains(playerId) || war.getDefenderPlayers().contains(playerId)) {
                Bukkit.getLogger().info("Player " + playerId + " is in war " + war.getId() + "with status " + war.getStatus());
                return war;
            }
        }
        return null;
    }
}