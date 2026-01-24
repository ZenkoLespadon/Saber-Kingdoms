// listeners/KingdomNpcProtectionListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

public class KingdomNpcProtectionListener implements Listener {

    private final NamespacedKey npcKey;

    public KingdomNpcProtectionListener(FactionsPlugin plugin) {
        this.npcKey = new NamespacedKey(plugin, "kingdom_join_npc");
    }

    // empêche tout dégât (feu, explosion, etc.)
    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Villager v)) return;
        Byte marker = v.getPersistentDataContainer().get(npcKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) e.setCancelled(true);
    }

    // empêche les coups directs des joueurs ou mobs
    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Villager v)) return;
        Byte marker = v.getPersistentDataContainer().get(npcKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) e.setCancelled(true);
    }
}
