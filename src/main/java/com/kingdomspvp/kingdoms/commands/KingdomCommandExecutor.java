package com.kingdomspvp.kingdoms.commands;

import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class KingdomCommandExecutor implements CommandExecutor {

    private final FactionsPlugin plugin;
    private final KingdomCommand rootCommand;

    public KingdomCommandExecutor(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.rootCommand = new KingdomCommand() {
            @Override
            public void perform(KingdomCommandContext context) {
                showHelp(context);
            }

            @Override
            public String getUsageTranslation() {
                return "Usage: /k <subcommand> [args]";
            }
        };

        this.rootCommand.addSubCommand(new JoinCommand(plugin));
        this.rootCommand.addSubCommand(new FlistCommand(plugin));
        // Ajoutez ici les autres commandes
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        KingdomCommandContext context = new KingdomCommandContext(sender, Arrays.asList(args), plugin);
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(context);
            return true;
        }
        rootCommand.execute(context);
        return true;
    }

    private void showHelp(KingdomCommandContext context) {
        if (context.sender instanceof Player) {
            Player player = (Player) context.sender;
            player.sendMessage("§6§l--------------------------------------------");
            player.sendMessage("§a§lKingdoms Help §7(1/1)");
            player.sendMessage("§6§l--------------------------------------------");
            for (KingdomCommand subCommand : rootCommand.subCommands) {
                player.sendMessage(subCommand.getHelpMessage());
            }
            player.sendMessage("§6§l--------------------------------------------");
        } else {
            context.msg("This command can only be used by a player.");
        }
    }
}
