package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.Arrays;

public class ClaimCommand extends KingdomCommand {

    // TODO : Ajouter le fait que le claim doit être à côté d'un autre claim du royaume
    // TODO : Ajouter une exception si la faction n'est pas dans le royaume
    // TODO : Ajouter une exception si le claim n'est pas trouvé

    public ClaimCommand() {
        this.aliases = Arrays.asList("claim");
        this.helpShort = ChatColor.GRAY + "Claim le chunk autour de toi pour ton royaume.";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.GRAY + "Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        // Vérification faction du joueur
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(context.player);
        if (fPlayer == null || fPlayer.getFaction() == null || fPlayer.getFaction().isWilderness()) {
            context.msg(ChatColor.RED + "Vous devez être dans une faction pour claim.");
            return;
        }

        String factionName = fPlayer.getFaction().getTag();
        Kingdom kingdom = KingdomsManager.getKingdomByFactionName(factionName);
        if (kingdom == null) {
            context.msg(ChatColor.RED + "Votre faction n'appartient à aucun royaume.");
            return;
        }

        // Trouver le claim actuel
        Location loc = context.player.getLocation();
        Claim claim = ClaimManager.getClaimByCoordinates(loc.getBlockX(), loc.getBlockZ());
        if (claim == null) {
            context.msg(ChatColor.RED + "Impossible de trouver le claim correspondant ici.");
            return;
        }

        // Vérifier propriété actuelle
        if (claim.getKingdomName() != null && !claim.getKingdomName().equalsIgnoreCase("None")) {
            context.msg(ChatColor.RED + "Ce chunk est déjà claim par un royaume.");
            return;
        }

        // Effectuer le claim
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
