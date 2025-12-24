package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class PowerCommand extends KingdomCommand {

    public PowerCommand() {
        this.aliases.add("power");
        this.helpShort = "Affiche votre power actuel.";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.RED + "Commande réservée aux joueurs.");
            return;
        }

        Player player = context.player;

        // Récupération du KPlayer associé au joueur
        if (!KPlayerManager.kPlayerInKPlayers(player)) {
            context.msg(ChatColor.RED + "Vous n'avez pas de KPlayer associé. Veuillez contacter un administrateur.");
            return;
        }

        KPlayer kPlayer = KPlayerManager.getKPlayerOfPlayer(player);
        Integer power = kPlayer.getPower();

        context.msg(ChatColor.GREEN + "Votre power est : " + ChatColor.YELLOW + power);
    }

    @Override
    public String getUsageTranslation() {
        return "Usage: /k power";
    }
}
