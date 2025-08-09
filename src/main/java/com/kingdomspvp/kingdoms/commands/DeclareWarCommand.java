package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.ChatColor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

public class DeclareWarCommand extends KingdomCommand {

    public DeclareWarCommand() {
        this.aliases      = Arrays.asList("declarewar");
        this.requiredArgs = Arrays.asList("royaume", "date", "heure");
        this.helpShort    = ChatColor.GRAY + "Déclarer la guerre à un autre royaume.";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        // 1) Vérifier que c'est un joueur.
        if (context.player == null) {
            context.msg(ChatColor.RED + "Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        WarManager.cleanupExpiredRegistrations();

        // 2) Vérifier qu'il est dans une faction valable.
        FPlayer fp = FPlayers.getInstance().getByPlayer(context.player);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) {
            context.msg(ChatColor.RED + "Vous devez être dans une faction pour déclarer la guerre.");
            return;
        }

        // 3) Récupérer le royaume attaquant.
        Faction playerFaction = fp.getFaction();
        Kingdom attacker = KingdomsManager.getKingdomByFactionName(playerFaction.getTag());
        if (attacker == null) {
            context.msg(ChatColor.RED + "Votre faction n'appartient à aucun royaume.");
            return;
        }

        // 4) Vérifier la bonne taille des arguments.
        if (context.args.size() < 3) {
            context.msg(ChatColor.RED + "Usage : " + getUsageTranslation());
            return;
        }

        // 5) Parser les arguments.
        String defenderName = context.args.get(0);
        String dateStr      = context.args.get(1);
        String timeStr      = context.args.get(2);

        if (defenderName.equalsIgnoreCase(attacker.getName())) {
            context.msg(ChatColor.RED + "Vous ne pouvez pas déclarer la guerre à votre propre royaume.");
            return;
        }

        Kingdom defender = KingdomsManager.getKingdomByName(defenderName);
        if (defender == null) {
            context.msg(ChatColor.RED + "Le royaume '" + defenderName + "' n'existe pas.");
            return;
        }

        if (WarManager.hasPendingWar(attacker, defender)) {
            context.msg(ChatColor.RED + "Il y a déjà une guerre programmée entre "
                    + attacker.getName() + " et " + defender.getName() + ".");
            return;
        }

        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(dateStr);   // format YYYY-MM-DD
            time = LocalTime.parse(timeStr);   // format HH:MM
        } catch (DateTimeParseException e) {
            context.msg(ChatColor.RED + "Date ou heure invalide. Formats : YYYY-MM-DD et HH:MM");
            return;

        }
        LocalDateTime startDateTime = LocalDateTime.of(date, time);
        LocalDateTime now = LocalDateTime.now();

        // Vérification du délai minimal
        if (startDateTime.isBefore(now.plus(WarManager.MIN_TIME_BEFORE_WAR))) {
            context.msg(ChatColor.RED + "Vous devez déclarer la guerre au moins "
                    + WarManager.MIN_TIME_BEFORE_WAR.toMinutes()
                    + " minutes à l'avance.");
            return;
        }

        // Vérification du délai maximal
        if (startDateTime.isAfter(now.plus(WarManager.MAX_TIME_BEFORE_WAR))) {
            context.msg(ChatColor.RED + "Vous ne pouvez pas déclarer la guerre plus de "
                    + WarManager.MAX_TIME_BEFORE_WAR.toMinutes()
                    + " minutes à l'avance.");
            return;
        }

        WarManager.declareWar(defender, attacker, startDateTime.toString());

        // 7) Afficher le récapitulatif.
        context.msg("");
        context.msg(ChatColor.GOLD   + "=== Déclaration de guerre ===");
        context.msg(ChatColor.GREEN  + "  Attaquant  : " + attacker.getColor() + attacker.getName());
        context.msg(ChatColor.RED    + "  Défenseur  : " + defender.getColor() + defender.getName());
        context.msg(ChatColor.AQUA   + "  Date       : " + dateStr);
        context.msg(ChatColor.AQUA   + "  Heure      : " + timeStr);
        context.msg(ChatColor.GOLD   + "============================");
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "declarewar "
                + ChatColor.WHITE + "<royaume> <YYYY-MM-DD> <HH:MM>";
    }
}
