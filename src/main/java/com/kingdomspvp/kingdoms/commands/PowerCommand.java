package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.utils.PowerCalculator;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

public class PowerCommand extends KingdomCommand {

    public PowerCommand() {
        this.aliases.add("power");
        this.helpShort = "Affiche votre power (0 à 100)";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        Player player = context.player;

        if (player == null) {
            context.msg(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return;
        }

        int total = PowerCalculator.compute(player);
        int pvp = PowerCalculator.computeKDA(player);
        int job = PowerCalculator.computeJobs(player);

        context.msg(ChatColor.GOLD + "Votre Power est " + ChatColor.GREEN + total + ChatColor.YELLOW + "/100.");
        context.msg(ChatColor.GRAY + "PvP : " + ChatColor.AQUA + pvp + ChatColor.DARK_GRAY + "/50");
        context.msg(ChatColor.GRAY + "Jobs : " + ChatColor.AQUA + job + ChatColor.DARK_GRAY + "/50");
    }

    @Override
    public String getUsageTranslation() {
        return "Usage: /k power";
    }

    @Override
    public List<String> tabComplete(KingdomCommandContext context) {
        return List.of();
    }
}
