package com.massivecraft.factions.cmd;

import com.kingdomspvp.kingdoms.services.FPlayerManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;

public class CmdLeave extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdLeave() {
        super();
        this.getAliases().addAll(Aliases.leave);

        this.setRequirements(new CommandRequirements.Builder(Permission.LEAVE)
                .playerOnly()
                .memberOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        if (KingdomsManager.playerInDefaultKingdom(context.player, KingdomsManager.getKingdomByFactionName(FPlayerManager.getFPlayerFaction(context.player).getTag()))) {
            context.msg("Vous ne pouvez pas quitter la faction par défaut du royaume");
            return;
        }
        if (context.fPlayer.getFaction().getFPlayers().size() == 1) {
            context.msg("Vous êtes le seul joueur dans la faction, vous devez la dissoudre");
            return;
        }
        FPlayerManager.getFPlayerKingdom(context.player).addPlayerToDefaultFaction(context.player);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.LEAVE_DESCRIPTION;
    }

}