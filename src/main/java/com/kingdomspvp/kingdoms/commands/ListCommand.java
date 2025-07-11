package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.services.KPlayerManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayers;
import org.bukkit.ChatColor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListCommand extends KingdomCommand {

    public ListCommand() {
        this.aliases.add("list");
        this.helpShort = "Affiche la liste des royaumes existants triés par puissance";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        List<Kingdom> kingdoms = KingdomsManager.getKingdoms();

        if (kingdoms.isEmpty()) {
            context.msg(ChatColor.RED + "Aucun royaume n'a été trouvé.");
            return;
        }

        // Calculer le power total de chaque royaume
        Map<Kingdom, Integer> kingdomPowerMap = kingdoms.stream()
                .collect(Collectors.toMap(
                        kingdom -> kingdom,
                        this::getKingdomPower
                ));

        // Trier les royaumes par power décroissant
        List<Map.Entry<Kingdom, Integer>> sortedKingdoms = kingdomPowerMap.entrySet().stream()
                .sorted(Map.Entry.<Kingdom, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        context.msg(ChatColor.GREEN + "Classement des royaumes par puissance :");
        for (Map.Entry<Kingdom, Integer> entry : sortedKingdoms) {
            Kingdom kingdom = entry.getKey();
            int power = entry.getValue();
            context.msg(ChatColor.YELLOW + "- " + ChatColor.GREEN + kingdom.getName()
                    + ChatColor.WHITE + " (Couleur: " + kingdom.getColor() + kingdom.getColor().name()
                    + ChatColor.WHITE + ", Power: " + ChatColor.GOLD + power + ChatColor.WHITE + ")");
        }
    }

    // Calcule la puissance totale d'un royaume
    private int getKingdomPower(Kingdom kingdom) {
        return KPlayerManager.getSortedKPlayers().stream()
                .filter(kPlayer -> kingdom.getFactions().stream()
                        .anyMatch(faction ->
                                FPlayers.getInstance().getAllFPlayers().stream()
                                        .filter(fPlayer -> fPlayer.getFaction().equals(faction))
                                        .anyMatch(fPlayer -> fPlayer.getPlayer() != null && fPlayer.getPlayer().equals(kPlayer.getPlayer()))
                        )
                )
                .mapToInt(KPlayer::getPower)
                .sum();
    }

    @Override
    public String getUsageTranslation() {
        return "/k list - Affiche le classement des royaumes par puissance";
    }
}