package com.kingdomspvp.kingdoms.commands;

import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
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

        this.rootCommand.addSubCommand(new JoinCommand());
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
        this.rootCommand.addSubCommand(new AutoClaimCommand(plugin));
        this.rootCommand.addSubCommand(new ReloadCommand(plugin));
        this.rootCommand.addSubCommand(new NpcJoinCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        KingdomCommandContext context = new KingdomCommandContext(sender, Arrays.asList(args), plugin);

        // /k help ou sans arguments → affichage de l’aide filtrée
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(context);
            return true;
        }

        // Exécution d’une sous-commande
        String sub = args[0].toLowerCase(Locale.ROOT);
        KingdomCommand subCmd = rootCommand.subCommands.stream()
                .filter(c -> c.aliases.stream().anyMatch(a -> a.equalsIgnoreCase(sub)))
                .findFirst()
                .orElse(null);

        if (subCmd == null || !subCmd.hasPermission(sender) ){
            // Commande inconnue ou sans permission → aide filtrée
            showHelp(context);
            return true;
        }

        rootCommand.execute(context);
        return true;
    }

    private void showHelp(KingdomCommandContext context) {
        if (!(context.sender instanceof Player)) {
            context.msg("Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        Player player = (Player) context.sender;
        List<KingdomCommand> allowed = rootCommand.subCommands.stream()
                .filter(sc -> !sc.isHidden())
                .filter(sc -> sc.hasPermission(player))
                .collect(Collectors.toList());

        player.sendMessage("§6§l--------------------------------------------");
        player.sendMessage("§a§lKingdoms Help");
        player.sendMessage("§6§l--------------------------------------------");

        if (allowed.isEmpty()) {
            player.sendMessage("§7Aucune commande disponible.");
        } else {
            for (KingdomCommand sub : allowed) {
                player.sendMessage(sub.getHelpMessage());
            }
        }

        player.sendMessage("§6§l--------------------------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<KingdomCommand> visible = this.rootCommand.subCommands.stream()
                .filter(sc -> !sc.isHidden())
                .filter(sc -> sc.hasPermission(sender))
                .collect(Collectors.toList());

        if (args.length == 0) {
            return visible.stream()
                    .flatMap(sc -> sc.aliases.stream())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return visible.stream()
                    .flatMap(sc -> sc.aliases.stream())
                    .filter(a -> a != null && a.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

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
