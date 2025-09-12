package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.gui.WarDeclareWizard;
import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.ClaimManager;
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
import java.util.List;

public class DeclareWarCommand extends KingdomCommand {

    public DeclareWarCommand() {
        this.aliases      = Arrays.asList("declarewar");
        this.requiredArgs = Arrays.asList("royaume", "date", "heure");
        this.helpShort    = ChatColor.GRAY + "Déclarer la guerre à un autre royaume.";
    }

    @Override
    public boolean validCall(KingdomCommandContext context) {
        // Accepte 0 arg (ouvre GUI) ou exactement 3 args (royaume, date, heure)
        if (context.args == null || context.args.isEmpty()) return true;
        if (context.args.size() == 3) return true;
        context.sender.sendMessage(getUsageTemplate());
        return false;
    }

    @Override
    public void perform(KingdomCommandContext context) {

        System.out.println("DEBUG: perform() called with args: " + context.args + context.args.size());

        if (context.player == null) {
            context.msg(ChatColor.RED + "Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        WarManager.cleanupExpiredRegistrations();

        FPlayer fp = FPlayers.getInstance().getByPlayer(context.player);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) {
            context.msg(ChatColor.RED + "Vous devez être dans une faction pour déclarer la guerre.");
            return;
        }
        Faction playerFaction = fp.getFaction();
        Kingdom attacker = KingdomsManager.getKingdomByFactionName(playerFaction.getTag());
        if (attacker == null) {
            context.msg(ChatColor.RED + "Votre faction n'appartient à aucun royaume.");
            return;
        }

        if (context.args.size() < 3) {
            WarDeclareWizard.openFor(context.player); // lance l’UI
            return;
        }


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

        List<Claim> attackable = ClaimManager.getDefenderClaimsAdjacentToAttacker(
                defender.getName(), attacker.getName()
        );
        if (attackable.isEmpty()) {
            context.msg(ChatColor.RED + "Aucune frontière commune : votre royaume n'a pas de claim adjacent à "
                    + defender.getName() + ". Déclaration refusée.");
            return;
        }

        LocalDate date;
        LocalTime time;
        try {
            String ds = dateStr.trim();
            if (ds.equalsIgnoreCase("TODAY")) {
                date = LocalDate.now();
            } else if (ds.equalsIgnoreCase("TOMORROW")) {
                date = LocalDate.now().plusDays(1);
            } else {
                date = LocalDate.parse(ds); // YYYY-MM-DD
            }
        } catch (DateTimeParseException ex) {
            context.msg(ChatColor.RED + "Date invalide. Utilisez YYYY-MM-DD, TODAY ou TOMORROW.");
            return;
        }
        try {
            time = LocalTime.parse(timeStr);   // HH:MM
        } catch (DateTimeParseException ex) {
            context.msg(ChatColor.RED + "Heure invalide. Format attendu : HH:MM");
            return;
        }

        LocalDateTime startDateTime = LocalDateTime.of(date, time);
        LocalDateTime now = LocalDateTime.now();

        if (startDateTime.isBefore(now.plus(WarManager.MIN_TIME_BEFORE_WAR))) {
            context.msg(ChatColor.RED + "Vous devez déclarer la guerre au moins "
                    + WarManager.MIN_TIME_BEFORE_WAR.toMinutes() + " minutes à l'avance.");
            return;
        }
        if (startDateTime.isAfter(now.plus(WarManager.MAX_TIME_BEFORE_WAR))) {
            context.msg(ChatColor.RED + "Vous ne pouvez pas déclarer la guerre plus de "
                    + WarManager.MAX_TIME_BEFORE_WAR.toMinutes() + " minutes à l'avance.");
            return;
        }

        War war = WarManager.declareWar(defender, attacker, startDateTime.toString(), attackable);
        WarManager.sendMessagetoPlayersOfKingdoms(war);
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "declarewar "
                + ChatColor.WHITE + "<royaume> <YYYY-MM-DD|TODAY|TOMORROW> <HH:MM>";
    }
}
