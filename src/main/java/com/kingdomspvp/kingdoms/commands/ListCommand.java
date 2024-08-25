package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import org.bukkit.ChatColor;

import java.util.List;

public class ListCommand extends KingdomCommand {

    public ListCommand() {
        this.aliases.add("list");
        this.helpShort = "Affiche la liste des royaumes existants";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        List<Kingdom> kingdoms = KingdomsManager.getKingdoms();

        if (kingdoms.isEmpty()) {
            context.msg(ChatColor.RED + "Aucun royaume n'a été trouvé.");
            return;
        }

        context.msg(ChatColor.GREEN + "Liste des royaumes :");
        for (Kingdom kingdom : kingdoms) {
            context.msg(ChatColor.YELLOW + "- " + ChatColor.GREEN + kingdom.getName() + ChatColor.WHITE + " (Couleur: " + kingdom.getColor() + kingdom.getColor().name() + ChatColor.WHITE + ")");
        }
    }

    @Override
    public String getUsageTranslation() {
        return "/k list - Affiche la liste des royaumes";
    }
}
