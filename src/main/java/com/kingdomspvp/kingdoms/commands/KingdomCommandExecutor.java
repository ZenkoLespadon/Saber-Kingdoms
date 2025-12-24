package com.kingdomspvp.kingdoms.commands;

import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class KingdomCommandExecutor implements CommandExecutor, TabCompleter {

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
        this.rootCommand.addSubCommand(new CreateCommand());
        this.rootCommand.addSubCommand(new ListCommand());
        this.rootCommand.addSubCommand(new PowerCommand());
        this.rootCommand.addSubCommand(new MembersCommand());
        this.rootCommand.addSubCommand(new ClaimCommand());
        this.rootCommand.addSubCommand(new DeclareWarCommand());
        this.rootCommand.addSubCommand(new WarsListCommand());
        this.rootCommand.addSubCommand(new WarHiddenJoinCommand());
        this.rootCommand.addSubCommand(new ClaimsVizCommand(plugin));
        this.rootCommand.addSubCommand(new TpToWarHiddenCommand());
        this.rootCommand.addSubCommand(new SpawnPointCommand());
        this.rootCommand.addSubCommand(new WarTestCommand());
        this.rootCommand.addSubCommand(new WarsListAdminCommand());
        this.rootCommand.addSubCommand(new WarEndCommand());
        this.rootCommand.addSubCommand(new SetKingdomCommand());
        this.rootCommand.addSubCommand(new AutoClaimCommand(plugin));    }

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
                if (subCommand.isHidden()) continue;
                if (!subCommand.hasPermission(player)) continue;
                player.sendMessage(subCommand.getHelpMessage());
            }
            player.sendMessage("§6§l--------------------------------------------");
        } else {
            context.msg("Cette commande ne peut être exécutée que par un joueur.");
        }
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        // Filtrer les sous-commandes visibles/permises
        List<KingdomCommand> visible = this.rootCommand.subCommands.stream()
                .filter(sc -> !sc.isHidden())
                .filter(sc -> sc.hasPermission(sender))
                .collect(Collectors.toList());

        // /k <TAB>  → proposer toutes les sous-commandes visibles
        if (args.length == 0) {
            return visible.stream()
                    .flatMap(sc -> sc.aliases.stream())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        // /k <prefix> <TAB>
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return visible.stream()
                    .flatMap(sc -> sc.aliases.stream())
                    .filter(a -> a != null && a.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        // Déléguer au subcommand correspondant (si visible/autorisé)
        String sub = args[0].toLowerCase(Locale.ROOT);
        KingdomCommand subCmd = visible.stream()
                .filter(c -> c.aliases.stream().anyMatch(a -> a.equalsIgnoreCase(sub)))
                .findFirst().orElse(null);
        if (subCmd == null) return Collections.emptyList();

        List<String> tail = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));
        KingdomCommandContext subCtx = new KingdomCommandContext(sender, tail, plugin);

        List<String> raw = subCmd.tabComplete(subCtx);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        String last = tail.get(tail.size() - 1).toLowerCase(Locale.ROOT);
        return raw.stream()
                .filter(s -> s != null && s.toLowerCase(Locale.ROOT).startsWith(last))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

}
