package com.kingdomspvp.kingdoms.commands;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.FPlayerManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FlistCommand extends KingdomCommand {

    private final FactionsPlugin plugin;

    public FlistCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.aliases.add("flist");
        this.helpShort = "Affiche la liste des factions du royaume";
        this.optionalArgs.put("kingdom", "");
    }

    @Override
    public void perform(KingdomCommandContext context) {
        CommandSender sender = context.sender;
        KingdomsManager kingdomsManager = plugin.getKingdomsManager();

        if (!(sender instanceof Player)) {
            context.msg("Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        Player player = (Player) sender;
        Faction playerFaction = FPlayerManager.getFPlayerFaction(player);

        if (playerFaction == null) {
            context.msg("Vous n'appartenez à aucune faction.");
            return;
        }

        if (context.args.isEmpty()) {
            // Affiche les factions du royaume du joueur
            String factionName = playerFaction.getTag();
            Kingdom kingdom = kingdomsManager.getKingdomByFactionName(factionName);
            if (kingdom == null) {
                context.msg("Le royaume pour votre faction n'a pas été trouvé.");
                return;
            }
            displayFactions(context, kingdom);
        } else {
            // Affiche les factions du royaume spécifié
            String kingdomName = context.args.get(0);
            Kingdom kingdom = kingdomsManager.getKingdomByName(kingdomName);
            if (kingdom == null) {
                context.msg("Le royaume spécifié n'existe pas.");
                return;
            }
            displayFactions(context, kingdom);
        }
    }

    private void displayFactions(KingdomCommandContext context, Kingdom kingdom) {
        context.msg(ChatColor.GRAY + "Factions dans le royaume " + kingdom.getColor() + kingdom.getName() + ChatColor.GRAY + " :");
        List<Faction> factions = kingdom.getFactions();
        if (factions.isEmpty()) {
            context.msg("Aucune faction trouvée.");
            return;
        }
        for (Faction faction : factions) {
            context.msg("- " + faction.getTag());
        }
    }

    @Override
    public String getUsageTranslation() {
        return "/k flist [kingdomName]";
    }

    @Override
    public String getHelpMessage() {
        return super.getHelpMessage();
    }
}
