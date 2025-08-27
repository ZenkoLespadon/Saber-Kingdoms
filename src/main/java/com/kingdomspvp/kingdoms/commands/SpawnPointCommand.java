// src/main/java/com/kingdomspvp/kingdoms/commands/SpawnPointCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class SpawnPointCommand extends KingdomCommand {

    public SpawnPointCommand() {
        this.aliases.add("spawnpoint");
        this.requiredArgs.add("nom_du_royaume");
        this.setHelpShort("Définit le point de respawn de tous les membres du royaume.");
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (!(context.sender instanceof Player)) {
            context.msg("Commande réservée aux joueurs.");
            return;
        }
        Player player = context.player;
        String kingdomName = context.args.get(0);
        Kingdom kingdom = KingdomsManager.getKingdomByName(kingdomName);
        if (kingdom == null) {
            context.msg("Royaume introuvable.");
            return;
        }

        Location loc = player.getLocation();
        kingdom.setSpawnPoint(loc); // Ajoute cette méthode dans Kingdom.java

        // Téléporte tous les membres du royaume au nouveau spawnpoint
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (KingdomsManager.getKingdomByFactionName(
                    com.massivecraft.factions.FPlayers.getInstance().getByPlayer(p).getFaction().getTag()
            ) == kingdom) {
                p.setBedSpawnLocation(loc, true);
                p.sendMessage("§aLe point de respawn de votre royaume a été mis à jour !");
            }
        }
        context.msg("§aSpawnpoint du royaume " + kingdomName + " défini.");
    }

    @Override
    public String getUsageTranslation() {
        return "/k spawnpoint <nom_du_royaume>";
    }
}