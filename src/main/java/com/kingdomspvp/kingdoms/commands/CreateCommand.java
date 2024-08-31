package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import org.bukkit.ChatColor;

public class CreateCommand extends KingdomCommand {

    public CreateCommand() {
        this.aliases.add("create");
        this.requiredArgs.add("name");
        this.setHelpShort("Create a new Kingdom.");
    }

    @Override
    public void perform(KingdomCommandContext context) {
        // Vérifier si l'expéditeur est un joueur
        if (context.player == null) {
            context.msg(ChatColor.RED + "This command can only be executed by a player.");
            return;
        }

        // Vérifier la permission
        if (!context.player.hasPermission("kingdoms.create")) {
            context.msg(ChatColor.RED + "You don't have permission to create a kingdom.");
            return;
        }

        // Récupérer le nom du royaume
        String kingdomName = context.args.get(0);

        // Vérifier si le royaume existe déjà
        if (KingdomsManager.getKingdomByName(kingdomName) != null) {
            context.msg(ChatColor.RED + "A kingdom with this name already exists.");
            return;
        }

        // Créer un nouveau royaume
        Kingdom kingdom = new Kingdom(kingdomName, ChatColor.GREEN); // Vous pouvez choisir une autre couleur ou la passer en argument
        KingdomsManager.addKingdom(kingdom);

        if (kingdom != null) {
            context.player.sendMessage(ChatColor.GRAY + "Vous avez rejoint le royaume : " + kingdom.getColor() + kingdom.getName());
            KingdomsManager.addPlayerToDefaultFactionOfKingdom(context.player, kingdom);
        } else {
            context.player.sendMessage(ChatColor.GRAY + "Le royaume spécifié n'existe pas.");
        }

        // Envoyer un message de confirmation
        context.msg(ChatColor.GREEN + "Kingdom " + kingdomName + " has been successfully created.");
    }

    @Override
    public String getUsageTranslation() {
        return "/k create <name>";
    }
}
