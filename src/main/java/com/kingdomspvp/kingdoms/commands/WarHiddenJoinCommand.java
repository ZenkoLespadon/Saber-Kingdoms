package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.ChatColor;
import java.util.Arrays;

public class WarHiddenJoinCommand extends KingdomCommand {

    public WarHiddenJoinCommand() {
        this.aliases = Arrays.asList("_warjoin");        // cachée derrière /k
        this.requiredArgs = Arrays.asList("warId");
        this.helpShort = null;
        this.hidden = true;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) return; // sécurité
        String warId = context.args.get(0);
        boolean ok = WarManager.registerPlayerIfEligible(warId, context.player);
        if (ok) {
            ChatUtil.sendWarMsg(context.player, ChatColor.GREEN + "Inscription enregistrée.");
            War war = WarManager.getWar(warId);
            context.player.spigot().sendMessage(WarManager.buildParticipantsMessage(war));

            // 2) Si guerre démarrée mais pas encore d'attaque, envoyer l’instruction aux ATTAQUANTS
            if (war != null
                    && war.getStatus() == WarStatus.INPROGRESS
                    && !war.hasCombatStarted()) {

                // Récupérer le royaume du joueur
                FPlayer fp = FPlayers.getInstance().getByPlayer(context.player);
                if (fp != null && fp.getFaction() != null && !fp.getFaction().isWilderness()) {
                    Kingdom playerKingdom = KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
                    if (playerKingdom != null && playerKingdom.equals(war.getAttackerKingdom())) {
                        context.player.spigot().sendMessage(WarManager.buildWaitForAttackMessage(war.getDefenderKingdom()));
                    }
                }
            }
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
