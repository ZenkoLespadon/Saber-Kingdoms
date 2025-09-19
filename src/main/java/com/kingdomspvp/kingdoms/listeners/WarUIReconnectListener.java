// listeners/WarUIReconnectListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class WarUIReconnectListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> WarRuntime.onPlayerJoinOrRespawn(e.getPlayer()),
                20L // 1s après login
        );
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(
                FactionsPlugin.getInstance(),
                () -> WarRuntime.onPlayerJoinOrRespawn(e.getPlayer()),
                20L // 1s après respawn (scoreboard réinitialisé)
        );
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        WarRuntime.onPlayerDeath(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        WarRuntime.onPlayerDeath(e.getPlayer().getUniqueId());
    }
}
