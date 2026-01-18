package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
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


    private static long ASSIST_WINDOW_SEC;
    private static boolean COUNT_SELF_DAMAGE;


    private final Map<UUID, Map<UUID, Long>> lastHits = new ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player damager = resolveDamager(e.getDamager());
        if (damager == null) return;

        // Ignore si aucun des deux n'est dans une guerre active
        if (!isInAnyActiveWar(victim.getUniqueId())
                && !isInAnyActiveWar(damager.getUniqueId())) {
            return;
        }

        lastHits
                .computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(damager.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        UUID vId = victim.getUniqueId();

        // Aucun killer clair (chute, lave, explosion, etc.)
        if (killer == null) {
            if (COUNT_SELF_DAMAGE) {
                WarRuntime.recordKill(vId, vId);
            }
            lastHits.remove(vId);
            return;
        }

        UUID kId = killer.getUniqueId();

        Optional<War> warOpt = findCommonActiveWar(kId, vId);
        if (warOpt.isEmpty()) {
            lastHits.remove(vId);
            return;
        }

        War war = warOpt.get();

        // Kill principal
        WarRuntime.recordKill(kId, vId);

        // Assists
        Map<UUID, Long> hits = lastHits.getOrDefault(vId, Collections.emptyMap());
        long now = System.currentTimeMillis();

        for (var entry : hits.entrySet()) {
            UUID assistId = entry.getKey();
            long lastHit = entry.getValue();

            if (assistId.equals(kId) || assistId.equals(vId)) continue;
            if (now - lastHit > ASSIST_WINDOW_SEC * 1000L) continue;
            if (!isSameSide(war, assistId, kId)) continue;

            WarRuntime.recordAssist(assistId);
        }

        lastHits.remove(vId);
    }

    private Player resolveDamager(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }

    private boolean isInAnyActiveWar(UUID id) {
        for (War w : WarManager.getWars().values()) {
            if (w.getStatus() != WarStatus.INPROGRESS) continue;
            if (w.getAttackerPlayers().contains(id)
                    || w.getDefenderPlayers().contains(id)) {
                return true;
            }
        }
        return false;
    }

    private Optional<War> findCommonActiveWar(UUID a, UUID b) {
        for (War w : WarManager.getWars().values()) {
            if (w.getStatus() != WarStatus.INPROGRESS) continue;

            boolean aIn = w.getAttackerPlayers().contains(a)
                    || w.getDefenderPlayers().contains(a);
            boolean bIn = w.getAttackerPlayers().contains(b)
                    || w.getDefenderPlayers().contains(b);

            if (!aIn || !bIn) continue;

            boolean aAtk = w.getAttackerPlayers().contains(a);
            boolean bAtk = w.getAttackerPlayers().contains(b);

            if (aAtk == bAtk) continue;

            return Optional.of(w);
        }
        return Optional.empty();
    }

    private boolean isSameSide(War w, UUID x, UUID y) {
        boolean xAtk = w.getAttackerPlayers().contains(x);
        boolean yAtk = w.getAttackerPlayers().contains(y);
        return xAtk == yAtk;
    }

    public static void reloadSettings() {
        var cfg = SettingsProvider.get().war().kda();
        ASSIST_WINDOW_SEC = cfg.assistWindowSeconds();
        COUNT_SELF_DAMAGE = cfg.countSelfDamage();
    }
}
