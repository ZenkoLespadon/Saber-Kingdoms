package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListCommand extends KingdomCommand {

    public ListCommand() {
        this.aliases.add("list");
        this.helpShort = "Affiche la liste des royaumes existants triés par nombre de membres";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        List<Kingdom> kingdoms = KingdomsManager.getKingdoms();

        if (kingdoms.isEmpty()) {
            context.msg(ChatColor.RED + "Aucun royaume n'a été trouvé.");
            return;
        }

        // Liste de tous les FPlayers
        List<FPlayer> allPlayers = (List<FPlayer>) FPlayers.getInstance().getAllFPlayers();

        // Associe chaque royaume à son nombre total de membres
        Map<Kingdom, Integer> countMap = new HashMap<>();

        for (Kingdom kingdom : kingdoms) {
            List<Faction> factions = kingdom.getFactions();
            if (factions == null || factions.isEmpty()) {
                countMap.put(kingdom, 0);
                continue;
            }

            int count = (int) allPlayers.stream()
                    .filter(fp -> fp.getFaction() != null && factions.contains(fp.getFaction()))
                    .count();

            countMap.put(kingdom, count);
        }

        // Tri par nombre de membres décroissant
        List<Map.Entry<Kingdom, Integer>> sorted = countMap.entrySet().stream()
                .sorted(Map.Entry.<Kingdom, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        context.msg(ChatColor.GOLD + "Classement des royaumes par nombre de membres :");

        for (Map.Entry<Kingdom, Integer> entry : sorted) {
            Kingdom k = entry.getKey();
            int members = entry.getValue();
            ChatColor col = k.getColor();

            context.msg(" - " + col + k.getName()
                    + ChatColor.WHITE + " ("
                    + ChatColor.GOLD + members + " membres" + ChatColor.WHITE + ")");
        }
    }

    @Override
    public String getUsageTranslation() {
        return "/k list - Affiche le classement des royaumes par puissance";
    }
}