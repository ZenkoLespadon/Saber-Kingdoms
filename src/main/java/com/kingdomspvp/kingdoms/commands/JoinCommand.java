package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.List;

public class JoinCommand extends KingdomCommand {

    public JoinCommand(FactionsPlugin plugin) {
        this.aliases = Arrays.asList("join");
        this.requiredArgs = Arrays.asList("kingdom");
        this.helpShort = ChatColor.GRAY + "Rejoindre un royaume";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.GRAY + "Commande réservée aux joueurs.");
            return;
        }

        if (context.args.size() < 1) {
            context.msg(ChatColor.GRAY + "Veuillez spécifier un royaume.");
            return;
        }

        String kingdomName = context.args.get(0);
        Kingdom kingdom = KingdomsManager.getKingdomByName(kingdomName);

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

    @Override
    public List<String> tabComplete(KingdomCommandContext context) {
        if (context.args.size() == 0) return java.util.Collections.emptyList();
        if (context.args.size() == 1) {
            return com.kingdomspvp.kingdoms.services.KingdomsManager.getAllKingdoms()
                    .stream().map(k -> k.getName()).collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }

}
