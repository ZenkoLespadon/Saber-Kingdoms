package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;

import java.util.Map;

public class WarClaimListener implements Listener {

    // Référence aux guerres à surveiller (id -> War), fournie/tenue par WarManager
    private final Map<String, War> activeDetectionWars;

    public WarClaimListener(Map<String, War> activeDetectionWars) {
        this.activeDetectionWars = activeDetectionWars;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // Log dans la console minecraft pour vérifier si la méthode est appelée
        System.out.println("PlayerMoveEvent détecté pour " + e.getPlayer().getName());

        // Ne traiter que si changement de cellule (CLAIM_SIZE = 128 -> >> 7)
        if ((e.getFrom().getBlockX() >> 7) == (e.getTo().getBlockX() >> 7) &&
                (e.getFrom().getBlockZ() >> 7) == (e.getTo().getBlockZ() >> 7)) {
            return;
        }

        System.out.println("1");


        if (activeDetectionWars.isEmpty()) return; // filet de sécurité

        System.out.println("2");

        Player p = e.getPlayer();
        FPlayer fp = FPlayers.getInstance().getByPlayer(p);

        System.out.println("3");

        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) return;

        Claim to = ClaimManager.getClaimByCoordinates(e.getTo().getBlockX(), e.getTo().getBlockZ());

        System.out.println("4");

        if (to == null) return;
        String toKingdom = to.getKingdomName();

        System.out.println("5");

        if (toKingdom == null || toKingdom.equalsIgnoreCase("None")) return;

        // Royaume du joueur
        var playerKingdom = KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());

        System.out.println("6");

        if (playerKingdom == null) return;

        // Chercher une guerre à surveiller qui correspond (INPROGRESS, pas encore démarrée, joueur attaquant dans claim défenseur)
        for (War w : activeDetectionWars.values()) {
                if (w.getStatus() != WarStatus.INPROGRESS || w.hasCombatStarted()) continue;

                boolean isDefClaim = w.getDefenderKingdom().getName().equalsIgnoreCase(toKingdom);
                boolean isAttacker = playerKingdom.equals(w.getAttackerKingdom());
                if (isDefClaim && isAttacker) {
                    WarManager.handleFirstClaimAttacked(w, to); // marquera et désactivera la détection si besoin
                    System.out.println("Détection de claim pour la guerre " + w.getId() + " dans le royaume " + toKingdom);
                    break;
                }
        }
    }
}
