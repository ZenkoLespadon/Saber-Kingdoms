package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.event.FactionDisbandEvent;
import org.bukkit.event.EventHandler;

import java.util.Objects;

public class FactionDisbandListeners {
    @EventHandler
    public void onFactionDisband(FactionDisbandEvent event) {
        // Récupère le nom de la faction qui est sur le point d'être supprimée
        String factionName = event.getFaction().getTag();

        // Parcourt tous les royaumes pour voir si cette faction est une faction par défaut
        for (Kingdom kingdom : KingdomsManager.getKingdoms()) {
            if (Objects.equals(kingdom.getDefaultFactionId(), event.getFaction().getId())){
                // Si c'est la faction par défaut, annule l'événement de suppression
                event.setCancelled(true);
                break;
            }
        }
    }
}
