package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.utils.PlayerUtil;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.Arrays;

import static com.kingdomspvp.kingdoms.services.ClaimManager.isCornerClaim;

public class ClaimCommand extends KingdomCommand {

    public ClaimCommand() {
        this.aliases = Arrays.asList("claim");
        this.helpShort = ChatColor.GRAY + "Claim le chunk autour de toi pour ton royaume.";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.GRAY + "Commande réservée aux joueurs.");
            return;
        }

        if (!PlayerUtil.isInOverworld(context.player)) {
            context.player.sendMessage(ChatColor.RED + "Cette commande ne peut être exécutée que dans l'Overworld.");
            return;
        }

        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(context.player);
        if (fPlayer == null || fPlayer.getFaction() == null || fPlayer.getFaction().isWilderness()) {
            context.msg(ChatColor.RED + "Vous devez être dans un royaume pour claim.");
            return;
        }

        if (!PlayerUtil.isFacLeaderWithMinMembers(fPlayer, context.player)) {
            return;
        }

        String factionName = fPlayer.getFaction().getTag();
        Kingdom kingdom = KingdomsManager.getKingdomByFactionName(factionName);
        if (kingdom == null) {
            context.msg(ChatColor.RED + "Votre faction n'appartient à aucun royaume. Veuillez contacter un administrateur.");
            return;
        }

        // --- Vérification limite de claims ---
        int maxClaims = ClaimManager.MAX_CLAIMS_PER_KINGDOM;
        int currentClaims = ClaimManager.getClaimsByKingdomName(kingdom.getName()).size();
        if (currentClaims >= maxClaims) {
            context.msg(ChatColor.RED + "Votre royaume a atteint la limite maximale de "
                    + maxClaims + " claims. Vous ne pouvez plus en créer.");
            return;
        }

        Location loc = context.player.getLocation();
        Claim claim = ClaimManager.getClaimByCoordinates(loc.getBlockX(), loc.getBlockZ());
        if (claim == null) {
            context.msg(ChatColor.RED + "Impossible de trouver le claim correspondant ici. Merci de signaler ce problème à un staff.");
            return;
        }

        if (claim.getKingdomName() != null && !claim.getKingdomName().equalsIgnoreCase("None")) {
            context.msg(ChatColor.RED + "Ce chunk est déjà claim par un royaume.");
            return;
        }

        boolean hasAnyClaim = ClaimManager.hasClaimForKingdom(kingdom.getName());
        if (!hasAnyClaim) {
            if (!isCornerClaim(claim)) {
                context.msg(ChatColor.RED + "Le premier claim d’un royaume doit être situé dans un coin de la carte.");
                return;
            }
        } else {
            if (!ClaimManager.isAdjacentToKingdomClaim(claim, kingdom.getName())) {
                context.msg(ChatColor.RED + "Vous ne pouvez claim que des chunks adjacents à ceux de votre royaume.");
                return;
            }
        }

        claim.setKingdomName(kingdom.getName());
        claim.setFactionName(factionName);
        ClaimManager.addClaim(claim);

        context.msg(ChatColor.GREEN + "Vous avez claim ce chunk pour le royaume "
                + kingdom.getColor() + kingdom.getName()
                + ChatColor.GREEN + " et la faction "
                + ChatColor.WHITE + factionName + ChatColor.GREEN + ".");
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "claim";
    }

    @Override
    public String getHelpMessage() {
        return super.getHelpMessage();
    }
}
