package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.WarManager;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import java.util.Arrays;

public class WarHiddenJoinCommand extends KingdomCommand {
    public WarHiddenJoinCommand() {
        this.aliases = Arrays.asList("_warjoin");        // cachée derrière /k
        this.requiredArgs = Arrays.asList("warId");
        this.helpShort = null; // pas d'affichage d'aide
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) return; // sécurité
        String warId = context.args.get(0);
        boolean ok = WarManager.registerPlayerIfEligible(warId, context.player);
        if (ok) {
            context.msg(ChatColor.GREEN + "Inscription enregistrée.");
            War war = WarManager.getWar(warId);
            context.player.spigot().sendMessage(WarManager.buildParticipantsMessage(war));
        } else {
            context.msg(ChatColor.RED + "Inscription refusée (non éligible ou guerre terminée).");
        }
    }

    @Override
    public String getUsageTranslation() {
        return ""; // inutile
    }

    @Override
    public String getHelpMessage() {
        return ""; // n'apparaît pas dans /k help
    }
}
