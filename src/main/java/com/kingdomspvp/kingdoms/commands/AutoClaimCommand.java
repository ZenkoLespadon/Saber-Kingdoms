// commands/AutoclaimCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.services.AutoClaimManager;
import com.kingdomspvp.kingdoms.utils.PlayerUtil;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Role;
import org.bukkit.ChatColor;

import java.util.Arrays;

import static com.kingdomspvp.kingdoms.services.KingdomsManager.MIN_FACTION_MEMBERS;

public class AutoClaimCommand extends KingdomCommand {

    public AutoClaimCommand(FactionsPlugin plugin) {
        this.aliases = Arrays.asList("autoclaim","ac");
        this.requiredArgs = new java.util.ArrayList<>();
        this.optionalArgs = new java.util.LinkedHashMap<>() {{ put("state","(on|off)"); }};
        this.helpShort = ChatColor.GRAY + "Active/désactive l’autoclaim";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) { context.msg(ChatColor.RED + "Joueur uniquement."); return; }

        FPlayer fp = com.massivecraft.factions.FPlayers.getInstance().getByPlayer(context.player);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) {
            context.msg(ChatColor.RED + "Vous devez être dans une faction.");
            return;
        }

        if (!PlayerUtil.isFacLeaderWithMinMembers(fp, context.player)) {
            return;
        }

        boolean newState;
        if (context.args.size() >= 1) {
            String s = context.args.get(0).toLowerCase();
            if (s.equals("on")) { AutoClaimManager.enable(context.player.getUniqueId()); newState = true; }
            else if (s.equals("off")) { AutoClaimManager.disable(context.player.getUniqueId()); newState = false; }
            else { newState = AutoClaimManager.toggle(context.player.getUniqueId()); }
        } else {
            newState = AutoClaimManager.toggle(context.player.getUniqueId());
        }
        context.player.sendMessage((newState ? ChatColor.GREEN + "Autoclaim activé" : ChatColor.YELLOW + "Autoclaim désactivé"));
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "autoclaim " + ChatColor.WHITE + "[on|off]";
    }
}
