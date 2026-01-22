package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.kingdomspvp.kingdoms.utils.PlayerUtil;
import org.bukkit.ChatColor;

import java.util.Arrays;

public class TpToWarHiddenCommand extends KingdomCommand {

    public TpToWarHiddenCommand() {
        this.aliases = Arrays.asList("_tp_to_war"); // cachée
        this.requiredArgs = Arrays.asList("warId");
        this.helpShort = null;
        this.hidden = true;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) return;

        if (!PlayerUtil.isInOverworld(context.player)) {
            context.player.sendMessage(ChatColor.RED + "Vous devez être dans l'Overworld pour rejoindre une guerre.");
            return;
        }

        if (context.args.isEmpty()) {
            context.msg(ChatColor.RED + "ID de guerre manquant.");
            return;
        }

        String warId = context.args.get(0);

        WarRuntime.tpToWar(warId, context.player);
    }

    @Override public String getUsageTranslation() { return ""; }
    @Override public String getHelpMessage() { return ""; }
}
