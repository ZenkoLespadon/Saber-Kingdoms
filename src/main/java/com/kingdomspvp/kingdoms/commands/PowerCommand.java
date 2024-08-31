package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class PowerCommand extends KingdomCommand {

    public PowerCommand() {
        this.aliases.add("power");
        this.helpShort = "Displays your current power.";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.RED + "This command can only be used by players.");
            return;
        }

        Player player = context.player;

        // Récupération du KPlayer associé au joueur
        if (!KPlayerManager.kPlayerInKPlayers(player)) {
            context.msg(ChatColor.RED + "You are not registered as a KPlayer.");
            return;
        }

        KPlayer kPlayer = KPlayerManager.getKPlayerOfPlayer(player);
        Integer power = kPlayer.getPower();

        context.msg(ChatColor.GREEN + "Your current power is: " + ChatColor.YELLOW + power);
    }

    @Override
    public String getUsageTranslation() {
        return "Usage: /k power";
    }
}
