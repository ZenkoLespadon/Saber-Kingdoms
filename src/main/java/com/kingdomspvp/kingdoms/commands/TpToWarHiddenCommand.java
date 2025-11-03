package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.FPlayerManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.Arrays;
import java.util.UUID;

public class TpToWarHiddenCommand extends KingdomCommand {
    private static final int OFFSET_ATTACKERS = 10; // 10 blocs à l'intérieur du claim attaquant

    public TpToWarHiddenCommand() {
        this.aliases = Arrays.asList("_tp_to_war"); // cachée
        this.requiredArgs = Arrays.asList("warId");
        this.helpShort = null;
        this.hidden = true;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) return;

        String warId = context.args.get(0);
        War w = WarManager.getWar(warId);
        if (w == null) {
            context.msg(ChatColor.RED + "Guerre introuvable.");
            return;
        }
        if (w.getStatus() != WarStatus.INPROGRESS || !w.hasCombatStarted()) {
            context.msg(ChatColor.RED + "Cette guerre n'est pas en phase de combat.");
            return;
        }

        // Éligibilité : inscrit & membre d’un des deux royaumes
        UUID uid = context.player.getUniqueId();
        boolean isRegistered = w.getAttackerPlayers().contains(uid) || w.getDefenderPlayers().contains(uid);
        if (!isRegistered) {
            context.msg(ChatColor.RED + "Tu n'es pas inscrit à cette guerre.");
            return;
        }
        Kingdom k = FPlayerManager.getPlayerKingdom(context.player);
        if (k == null || (!k.equals(w.getAttackerKingdom()) && !k.equals(w.getDefenderKingdom()))) {
            context.msg(ChatColor.RED + "Tu n'appartiens pas à un royaume impliqué.");
            return;
        }

        // Récup claim attaqué
        Claim attacked = WarManager.getAttackedClaim(w);
        if (attacked == null) {
            context.msg(ChatColor.RED + "Le claim à défendre n'est pas disponible.");
            return;
        }

        Location tp;
        if (k.equals(w.getDefenderKingdom())) {
            // Défenseurs -> centre du claim attaqué
            tp = WarManager.getClaimCenter(attacked);
        } else {
            // Attaquants -> claim adjacent appartenant à l'attaquant, à 10 blocs de la frontière
            Claim staging = WarManager.findAdjacentAttackerClaim(w, attacked);
            if (staging == null) {
                context.msg(ChatColor.RED + "Aucun claim attaquant adjacent trouvé pour le point de ralliement.");
                return;
            }
            tp = WarManager.getStagingPointNearBorder(staging, attacked, OFFSET_ATTACKERS);
        }

        context.player.teleport(tp);
        context.msg(ChatColor.GOLD + "Téléportation vers la zone de guerre.");
    }

    @Override public String getUsageTranslation() { return ""; }
    @Override public String getHelpMessage() { return ""; }
}
