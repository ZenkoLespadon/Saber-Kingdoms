package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import org.bukkit.ChatColor;

import java.util.List;

public class MembersCommand extends KingdomCommand {

    public MembersCommand() {
        this.aliases.add("members");
        this.helpShort = "Displays the list of kingdom members sorted by power.";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        // Assumons que chaque joueur appartient à un royaume
        List<KPlayer> sortedKPlayers = KPlayerManager.getSortedKPlayers();

        if (sortedKPlayers.isEmpty()) {
            context.msg(ChatColor.RED + "There are no members in the kingdom.");
            return;
        }

        context.msg(ChatColor.GOLD + "Kingdom Members Sorted by Power:");

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
