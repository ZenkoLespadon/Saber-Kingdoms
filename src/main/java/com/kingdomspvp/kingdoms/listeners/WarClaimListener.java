package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarClaimListener implements Listener {

    private final Map<String, War> activeDetectionWars;

    // Suivi des joueurs actuellement DANS le claim attaqué (par guerre)
    private final Map<String, Set<UUID>> insideAttackedByWar = new ConcurrentHashMap<>();

    public WarClaimListener(Map<String, War> activeDetectionWars) {
        this.activeDetectionWars = activeDetectionWars;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // traiter seulement aux changements de cellule (CLAIM_SIZE=128 -> >>7)
        if ((e.getFrom().getBlockX() >> 7) == (e.getTo().getBlockX() >> 7)
                && (e.getFrom().getBlockZ() >> 7) == (e.getTo().getBlockZ() >> 7)) {
            return;
        }

        Player p = e.getPlayer();
        FPlayer fp = FPlayers.getInstance().getByPlayer(p);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) return;

        // -------- 1) Détection du premier assaut (comportement existant) --------
        if (!activeDetectionWars.isEmpty()) {
            Claim to = ClaimManager.getClaimByCoordinates(e.getTo().getBlockX(), e.getTo().getBlockZ());
            if (to != null) {
                var playerKingdom = KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
                if (playerKingdom != null) {
                    for (War w : activeDetectionWars.values()) {
                        if (w.getStatus() != WarStatus.INPROGRESS || w.hasCombatStarted()) continue;
                        boolean isDefClaim = w.getDefenderKingdom().getName().equalsIgnoreCase(to.getKingdomName());
                        boolean isAttacker = playerKingdom.equals(w.getAttackerKingdom());
                        if (isDefClaim && isAttacker) {
                            WarManager.handleFirstClaimAttacked(w, to);
                            break;
                        }
                    }
                }
            }
        }

        // -------- 2) Annonces entrée/sortie du claim attaqué pendant la guerre --------
        var playerKingdom = KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
        if (playerKingdom == null) return;

        // On parcourt toutes les guerres en cours (qu’elles soient en fenêtre de détection ou en combat)
        for (War w : WarManager.getWars().values()) {
            if (w.getStatus() != WarStatus.INPROGRESS || !w.hasCombatStarted()) continue;

            Claim attacked = WarManager.getAttackedClaim(w);
            if (attacked == null) continue;

            // Filtrer aux joueurs des royaumes concernés
            boolean isConcerned = playerKingdom.equals(w.getAttackerKingdom()) || playerKingdom.equals(w.getDefenderKingdom());
            if (!isConcerned) continue;

            // Calcul état "dans le claim attaqué" AVANT/APRÈS
            boolean wasInside = isInClaim(e.getFrom().getBlockX(), e.getFrom().getBlockZ(), attacked);
            boolean nowInside = isInClaim(e.getTo().getBlockX(),   e.getTo().getBlockZ(),   attacked);

            if (wasInside == nowInside) continue;

            insideAttackedByWar.computeIfAbsent(w.getId(), k -> ConcurrentHashMap.newKeySet());
            Set<UUID> set = insideAttackedByWar.get(w.getId());

            p.sendMessage(getMessageOnMove(w, !wasInside && nowInside));
        }
    }



    private static String getMessageOnMove(War war, boolean entering) {
        return entering
                ? ChatColor.GOLD + "Vous entrez dans le claim du Royaume " + war.getDefenderKingdom().getColor() + war.getDefenderKingdom().getName() + "."
                : ChatColor.GOLD + "Vous sortez du claim du Royaume " + war.getDefenderKingdom().getColor() + war.getDefenderKingdom().getName() + ".";
    }

    private static boolean isInClaim(int blockX, int blockZ, Claim c) {
        Claim here = ClaimManager.getClaimByCoordinates(blockX, blockZ);
        return here != null && here.getGridX() == c.getGridX() && here.getGridZ() == c.getGridZ();
    }
}
