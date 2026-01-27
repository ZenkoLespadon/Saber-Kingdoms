package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.services.AutoClaimManager;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.utils.PlayerUtil;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.ChatColor;

import java.util.Arrays;

public class AutoClaimCommand extends KingdomCommand {

    public AutoClaimCommand(com.massivecraft.factions.FactionsPlugin plugin) {
        this.aliases = Arrays.asList("autoclaim","ac");
        this.requiredArgs = new java.util.ArrayList<>();
        this.optionalArgs = new java.util.LinkedHashMap<>() {{ put("state","(on|off)"); }};
        this.helpShort = ChatColor.GRAY + "Active/désactive l’autoclaim";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.RED + "Joueur uniquement.");
            return;
        }

        if (!PlayerUtil.isInOverworld(context.player)) {
            context.player.sendMessage(ChatColor.RED + "Cette commande ne peut être exécutée que dans l'Overworld.");
            return;
        }

        FPlayer fp = FPlayers.getInstance().getByPlayer(context.player);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) {
            context.msg(ChatColor.RED + "Vous devez être dans une faction.");
            return;
        }

        if (!PlayerUtil.isFacLeaderWithMinMembers(fp, context.player)) {
            return;
        }

        Kingdom kingdom = KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());

        if (kingdom != null) {
            if (!PlayerUtil.isFacLeaderWithMinMembers(kingdom, fp, context.player)) {
                return;
            }
        }

        if (kingdom != null){
            int MaxClaimsPerKingdom = ClaimManager.MAX_CLAIMS_PER_KINGDOM;
            int count = ClaimManager.getClaimsByKingdomName(kingdom.getName()).size();
            if (count >= MaxClaimsPerKingdom) {
                context.player.sendMessage(ChatColor.RED +
                        "Votre royaume a atteint la limite maximale de " + MaxClaimsPerKingdom + " claims. " +
                        "L’autoclaim ne peut pas être activé.");
                AutoClaimManager.disable(context.player.getUniqueId());
                return;
            }
        }

        boolean newState;

        if (context.args.size() >= 1) {
            String s = context.args.get(0).toLowerCase();
            if (s.equals("on")) {
                newState = true;
            } else if (s.equals("off")) {
                newState = false;
            } else {
                newState = AutoClaimManager.toggle(context.player.getUniqueId());
            }
        } else {
            newState = !AutoClaimManager.isEnabled(context.player.getUniqueId());
        }

        if (newState) {
            AutoClaimManager.enable(context.player.getUniqueId());
            context.player.sendMessage(ChatColor.GREEN + "Autoclaim activé");
        } else {
            AutoClaimManager.disable(context.player.getUniqueId());
            context.player.sendMessage(ChatColor.YELLOW + "Autoclaim désactivé");
        }
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "autoclaim " + ChatColor.WHITE + "[on|off]";
    }
}
