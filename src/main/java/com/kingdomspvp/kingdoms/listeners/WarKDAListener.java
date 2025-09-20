// listeners/WarKDAListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WarKDAListener implements Listener {

    private static final long ASSIST_WINDOW_SEC = 10L;

    // victim -> (damager -> lastHitMillis)
    private final Map<UUID, Map<UUID, Long>> lastHits = new ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player damager = resolveDamager(e.getDamager());
        if (damager == null) return;

        // Seulement si au moins une guerre en cours concerne victim ou damager (évite bruit)
        if (!isInAnyActiveWar(victim.getUniqueId()) && !isInAnyActiveWar(damager.getUniqueId())) return;

        lastHits.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(damager.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller(); // Bukkit calcule le killer (épée, flèche, etc.)
        if (killer == null) {
            // Pas de killer clair -> purge juste les traces récentes
            lastHits.remove(victim.getUniqueId());
            return;
        }

        UUID vId = victim.getUniqueId();
        UUID kId = killer.getUniqueId();

        // Vérifie qu'ils sont dans une guerre en cours et adversaires
        Optional<War> warOpt = findCommonActiveWar(kId, vId);
        if (!warOpt.isPresent()) {
            lastHits.remove(vId);
            return;
        }

        // Kill
        WarRuntime.recordKill(kId, vId);

        // Assists (a frappé victim dans la fenêtre et ≠ killer & ≠ victim & côté killer)
        Map<UUID, Long> map = lastHits.getOrDefault(vId, Collections.emptyMap());
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : map.entrySet()) {
            UUID assistId = en.getKey();
            if (assistId.equals(kId) || assistId.equals(vId)) continue;
            if (now - en.getValue() > ASSIST_WINDOW_SEC * 1000L) continue;

            // Même guerre et même camp que killer ?
            if (!isSameSide(warOpt.get(), assistId, kId)) continue;

            WarRuntime.recordAssist(assistId);
        }

        // Nettoyage
        lastHits.remove(vId);
    }

    private Player resolveDamager(org.bukkit.entity.Entity damagerEntity) {
        if (damagerEntity instanceof Player) return (Player) damagerEntity;
        if (damagerEntity instanceof Projectile) {
            ProjectileSource src = ((Projectile) damagerEntity).getShooter();
            if (src instanceof Player) return (Player) src;
        }
        return null;
    }

    private boolean isInAnyActiveWar(UUID id) {
        for (War w : WarManager.getWars().values()) {
            if (w.getStatus() != WarStatus.INPROGRESS) continue;
            if (w.getAttackerPlayers().contains(id) || w.getDefenderPlayers().contains(id)) return true;
        }
        return false;
    }

    private Optional<War> findCommonActiveWar(UUID a, UUID b) {
        for (War w : WarManager.getWars().values()) {
            if (w.getStatus() != WarStatus.INPROGRESS) continue;
            boolean aIn = w.getAttackerPlayers().contains(a) || w.getDefenderPlayers().contains(a);
            boolean bIn = w.getAttackerPlayers().contains(b) || w.getDefenderPlayers().contains(b);
            if (!aIn || !bIn) continue;
            boolean aAtk = w.getAttackerPlayers().contains(a);
            boolean bAtk = w.getAttackerPlayers().contains(b);
            if (aAtk == bAtk) continue; // même camp -> pas un kill de guerre
            return Optional.of(w);
        }
        return Optional.empty();
    }

    private boolean isSameSide(War w, UUID x, UUID y) {
        boolean xAtk = w.getAttackerPlayers().contains(x);
        boolean yAtk = w.getAttackerPlayers().contains(y);
        return xAtk == yAtk;
    }
}
