package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.gui.WarDeclareWizard;
import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
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
        if (context.args == null || context.args.isEmpty()) return true;
        if (context.args.size() == 3) return true;
        context.sender.sendMessage(getUsageTemplate());
        return false;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            ChatUtil.sendWarMsg(null, ChatColor.RED + "Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        WarManager.cleanupExpiredRegistrations();

        FPlayer fp = FPlayers.getInstance().getByPlayer(context.player);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Vous devez être dans une faction pour déclarer la guerre.");
            return;
        }
        Faction playerFaction = fp.getFaction();
        Kingdom attacker = KingdomsManager.getKingdomByFactionName(playerFaction.getTag());
        if (attacker == null) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Votre faction n'appartient à aucun royaume.");
            return;
        }

        if (context.args.size() < 3) {
            WarDeclareWizard.openFor(context.player);
            return;
        }

        String defenderName = context.args.get(0);
        String dateStr      = context.args.get(1);
        String timeStr      = context.args.get(2);

        if (defenderName.equalsIgnoreCase(attacker.getName())) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Vous ne pouvez pas déclarer la guerre à votre propre royaume.");
            return;
        }

        Kingdom defender = KingdomsManager.getKingdomByName(defenderName);
        if (defender == null) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Le royaume '" + defenderName + "' n'existe pas.");
            return;
        }

        if (WarManager.hasPendingWar(attacker, defender)) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Il y a déjà une guerre programmée entre "
                    + ChatUtil.kingdomName(attacker) + ChatColor.RED + " et " + ChatUtil.kingdomName(defender) + ChatColor.RED + ".");
            return;
        }

        List<Claim> attackable = ClaimManager.getDefenderClaimsAdjacentToAttacker(
                defender.getName(), attacker.getName()
        );
        if (attackable.isEmpty()) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Aucune frontière commune : votre royaume n'a pas de claim adjacent à "
                    + ChatUtil.kingdomName(defender) + ChatColor.RED + ". Déclaration refusée.");
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
                date = LocalDate.parse(ds);
            }
        } catch (DateTimeParseException ex) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Date invalide. Utilisez YYYY-MM-DD, TODAY ou TOMORROW.");
            return;
        }
        try {
            time = LocalTime.parse(timeStr);
        } catch (DateTimeParseException ex) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Heure invalide. Format attendu : HH:MM");
            return;
        }

        LocalDateTime startDateTime = LocalDateTime.of(date, time);
        LocalDateTime now = LocalDateTime.now();

        if (startDateTime.isBefore(now.plus(WarManager.MIN_TIME_BEFORE_WAR))) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Vous devez déclarer la guerre au moins "
                    + WarManager.MIN_TIME_BEFORE_WAR.toMinutes() + " minutes à l'avance.");
            return;
        }
        if (startDateTime.isAfter(now.plus(WarManager.MAX_TIME_BEFORE_WAR))) {
            ChatUtil.sendWarMsg(context.player, ChatColor.RED + "Vous ne pouvez pas déclarer la guerre plus de "
                    + WarManager.MAX_TIME_BEFORE_WAR.toMinutes() + " minutes à l'avance.");
            return;
        }

        War war = WarManager.declareWar(defender, attacker, startDateTime.toString(), attackable);
        WarManager.sendMessagetoPlayersOfKingdoms(war);

        ChatUtil.sendWarMsg(context.player,
                ChatColor.GREEN + "Guerre déclarée contre " + ChatColor.AQUA + ChatUtil.kingdomName(defender)
                        + ChatColor.GREEN + " le " + ChatColor.AQUA + startDateTime.toLocalDate()
                        + ChatColor.GREEN + " à " + ChatColor.AQUA + startDateTime.toLocalTime());
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "declarewar "
                + ChatColor.WHITE + "<royaume> <YYYY-MM-DD|TODAY|TOMORROW> <HH:MM>";
    }
}
