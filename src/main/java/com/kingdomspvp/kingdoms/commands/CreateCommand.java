package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import org.bukkit.ChatColor;

public class CreateCommand extends KingdomCommand {

    public CreateCommand() {
        this.aliases.add("create");
        this.requiredArgs.add("name");
        this.requiredArgs.add("color");
        this.setHelpShort("Create a new Kingdom.");
    }

    // Java - src/main/java/com/kingdomspvp/kingdoms/commands/CreateCommand.java
    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.RED + "This command can only be executed by a player.");
            return;
        }
        if (!context.player.hasPermission("kingdoms.create")) {
            context.msg(ChatColor.RED + "You don't have permission to create a kingdom.");
            return;
        }
        if (context.args.size() < 2) {
            context.msg(ChatColor.RED + "Usage: /k create <name> <color>");
            return;
        }

        String kingdomName = context.args.get(0);
        String colorName = context.args.get(1).toUpperCase();

        if (KingdomsManager.getKingdomByName(kingdomName) != null) {
            context.msg(ChatColor.RED + "A kingdom with this name already exists.");
            return;
        }

        ChatColor color;
        try {
            color = ChatColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            context.msg(ChatColor.RED + "Couleur invalide. Exemples : green, red, blue, gold, yellow...");
            return;
        }

        Kingdom kingdom = new Kingdom(kingdomName, color);
        KingdomsManager.addKingdom(kingdom);

        context.player.sendMessage(ChatColor.GRAY + "Vous avez rejoint le royaume : " + kingdom.getColor() + kingdom.getName());
        KingdomsManager.addPlayerToDefaultFactionOfKingdom(context.player, kingdom);

        context.msg(ChatColor.GREEN + "Kingdom " + kingdomName + " a été créé avec la couleur " + color + color.name().toLowerCase() + ChatColor.GREEN + ".");
    }

    @Override
    public String getUsageTranslation() {
        return "/k create <name> <color>";
    }

}
