package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class MembersCommand extends KingdomCommand {

    public MembersCommand() {
        this.aliases.add("members");
        this.helpShort = "Affiche la liste des membres d'un royaume triés par power";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        Player player = context.player;
        if (player == null) {
            context.msg(ChatColor.RED + "Commande réservée aux joueurs.");
            return;
        }

        Kingdom kingdom = KingdomsManager.getKingdomOfPlayer(player);
        if (kingdom == null) {
            context.msg(ChatColor.RED + "Vous n'appartenez à aucun royaume.");
            return;
        }

        List<Faction> factions = kingdom.getFactions();
        if (factions == null || factions.isEmpty()) {
            context.msg(ChatColor.RED + "Ce royaume n'a aucune faction.");
            return;
        }

        List<FPlayer> members = FPlayers.getInstance().getAllFPlayers().stream()
                .filter(fp -> fp.getFaction() != null && factions.contains(fp.getFaction()))
                .collect(Collectors.toList());

        if (members.isEmpty()) {
            context.msg(ChatColor.RED + "Aucun membre trouvé dans ce royaume.");
            return;
        }

        ChatColor kc = kingdom.getColor();
        int size = members.size();

        context.msg(ChatColor.GOLD + "Royaume " + kc + kingdom.getName()
                + ChatColor.GOLD + " (" + ChatColor.GREEN + size + ChatColor.GOLD + " membres)");

        for (FPlayer fp : members) {
            context.msg(kc + "- " + fp.getName());
        }
    }



    @Override
    public String getUsageTranslation() {
        return "Usage: /k members";
    }
}
