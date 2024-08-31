package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.ChatColor;

import java.util.Arrays;

public class JoinCommand extends KingdomCommand {

    public JoinCommand(FactionsPlugin plugin) {
        this.aliases = Arrays.asList("join");
        this.requiredArgs = Arrays.asList("kingdom");
        this.helpShort = ChatColor.GRAY + "Rejoindre un royaume";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.GRAY + "Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        if (context.args.size() < 1) {
            context.msg(ChatColor.GRAY + "Veuillez spécifier un royaume.");
            return;
        }

        String kingdomName = context.args.get(0);
        Kingdom kingdom = context.plugin.getKingdomsManager().getKingdomByName(kingdomName);

        if (kingdom != null) {
            context.player.sendMessage(ChatColor.GRAY + "Vous avez rejoint le royaume : " + kingdom.getColor() + kingdom.getName());
            KingdomsManager.addPlayerToDefaultFactionOfKingdom(context.player, kingdom);
            KPlayerManager.createKPlayer(context.player);
        } else {
            context.player.sendMessage(ChatColor.GRAY + "Le royaume spécifié n'existe pas.");
        }
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "join " + ChatColor.WHITE + "<kingdom>";
    }

    @Override
    public String getHelpMessage() {
        return super.getHelpMessage();
    }

}
