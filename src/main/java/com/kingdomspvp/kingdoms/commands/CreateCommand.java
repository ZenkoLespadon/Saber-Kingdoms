package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import org.bukkit.ChatColor;

public class CreateCommand extends KingdomCommand {

    public CreateCommand() {
        this.aliases.add("create");
        this.requiredArgs.add("name");
        this.requiredArgs.add("color");
        this.setHelpShort("Créer un nouveau royaume.");
        this.permission = "kingdoms.admin.create";
    }

    // Java - src/main/java/com/kingdomspvp/kingdoms/commands/CreateCommand.java
    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.RED + "Commande réservée aux joueurs.");
            return;
        }
        if (!context.player.hasPermission(this.permission)) {
            context.msg(ChatColor.RED + "Vous n'avez pas la permission de créer un royaume.");
            return;
        }
        if (context.args.size() < 2) {
            context.msg(ChatColor.RED + "Usage: /k create <name> <color>");
            return;
        }

        String kingdomName = context.args.get(0);
        String colorName = context.args.get(1).toUpperCase();

        if (KingdomsManager.getKingdomByName(kingdomName) != null) {
            context.msg(ChatColor.RED + "Un royaume avec ce nom existe déjà.");
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
        return "/k create <nom> <couleur>";
    }

    @Override
    public java.util.List<String> tabComplete(KingdomCommandContext context) {
        int n = context.args.size();
        if (n == 0) return java.util.Collections.emptyList(); // nom libre
        if (n == 1) return java.util.Collections.emptyList(); // nom libre

        if (n == 2) {
            String pref = context.args.get(1).toLowerCase(java.util.Locale.ROOT);
            java.util.List<String> colors = new java.util.ArrayList<>();
            for (org.bukkit.ChatColor c : org.bukkit.ChatColor.values()) {
                String name = c.name().toLowerCase(java.util.Locale.ROOT);
                // on garde seulement les couleurs "nommées" utiles (pas les formats & codes spéciaux)
                if (name.matches("(?i)black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white")) {
                    colors.add(name);
                }
            }
            return colors.stream()
                    .filter(s -> s.startsWith(pref))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return java.util.Collections.emptyList();
    }


}
