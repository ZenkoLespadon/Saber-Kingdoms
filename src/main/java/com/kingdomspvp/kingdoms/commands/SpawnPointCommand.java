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

        if (!player.hasPermission("kingdoms.admin.spawnpoint")) {
            context.msg("§cVous n'avez pas la permission d'exécuter cette commande.");
            return;
        }

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

    @Override
    public java.util.List<String> tabComplete(KingdomCommandContext context) {
        int n = context.args.size();
        if (n == 0) return java.util.Collections.emptyList();
        if (n == 1) {
            String pref = context.args.get(0).toLowerCase(java.util.Locale.ROOT);
            return com.kingdomspvp.kingdoms.services.KingdomsManager.getKingdoms().stream()
                    .map(com.kingdomspvp.kingdoms.model.Kingdom::getName)
                    .filter(nm -> nm.toLowerCase(java.util.Locale.ROOT).startsWith(pref))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return java.util.Collections.emptyList();
    }

}