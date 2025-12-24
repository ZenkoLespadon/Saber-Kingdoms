package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import org.bukkit.ChatColor;

import java.util.List;

public class MembersCommand extends KingdomCommand {

    public MembersCommand() {
        this.aliases.add("members");
        this.helpShort = "Affiche la liste des membres d'un royaume triés par power";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        // Assumons que chaque joueur appartient à un royaume
        List<KPlayer> sortedKPlayers = KPlayerManager.getSortedKPlayers();

        if (sortedKPlayers.isEmpty()) {
            context.msg(ChatColor.RED + "Il n'y a personne dans le royaume.");
            return;
        }

        context.msg(ChatColor.GOLD + "Membres du royaume triés par power:");

        for (KPlayer kPlayer : sortedKPlayers) {
            String playerName = kPlayer.getPlayer().getName();
            int power = kPlayer.getPower();
            context.msg(ChatColor.YELLOW + playerName + ": " + ChatColor.GREEN + power + " power");
        }
    }

    @Override
    public String getUsageTranslation() {
        return "Usage: /k members";
    }
}
