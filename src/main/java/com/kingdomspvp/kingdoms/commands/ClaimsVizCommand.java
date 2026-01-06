// src/main/java/com/kingdomspvp/kingdoms/commands/ClaimsVizCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.utils.ClaimVisualization;
import com.kingdomspvp.kingdoms.utils.SettingsProvider;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimsVizCommand extends KingdomCommand {

    private static Integer globalTaskId = null;
    private final FactionsPlugin plugin;

    public ClaimsVizCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        ClaimVisualization.init(plugin); // injection sûre du Plugin
        this.aliases = Arrays.asList("vizclaims", "vizall");
        this.requiredArgs = Collections.emptyList();
        this.helpShort = ChatColor.GRAY + "Trace tous les claims via particules (visible par tous).";
        this.optionalArgs.put("seconds", "");
    }

    @Override
    public void perform(KingdomCommandContext context) {

        if (context.player == null) {
            context.msg(ChatColor.RED + "Uniquement en jeu.");
            return;
        }

        // ---- STOP ----
        if (!context.args.isEmpty() && "stop".equalsIgnoreCase(context.args.get(0))) {
            stopGlobal();
            context.msg(ChatColor.YELLOW + "Visualisation arrêtée pour tous.");
            return;
        }

        var vizCfg = SettingsProvider.get().commands().vizclaims();
        int min = vizCfg.minSeconds();
        int max = vizCfg.maxSeconds();
        int seconds = vizCfg.defaultSeconds();

        // ---- ARGUMENT SECONDS ----
        if (!context.args.isEmpty()) {
            String arg = context.args.get(0);

            int parsed;
            try {
                parsed = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                context.msg(ChatColor.RED + "Valeur invalide : '" + arg + "'");
                context.msg(ChatColor.GRAY + "Utilisation : /k vizclaims <seconds>");
                context.msg(ChatColor.GRAY + "Plage autorisée : " + min + " à " + max + " secondes.");
                return;
            }

            if (parsed < min) {
                context.msg(ChatColor.RED + "Durée trop courte : " + parsed + "s");
                context.msg(ChatColor.GRAY + "Minimum autorisé : " + min + " secondes.");
                return;
            }

            if (parsed > max) {
                context.msg(ChatColor.RED + "Durée trop longue : " + parsed + "s");
                context.msg(ChatColor.GRAY + "Maximum autorisé : " + max + " secondes.");
                return;
            }

            seconds = parsed;
        }

        // ---- LANCEMENT ----
        stopGlobal();

        final int periodTicks = vizCfg.periodTicks();
        final int ticksPerSecond = 20;
        final int maxRuns = (seconds * ticksPerSecond) / periodTicks;

        globalTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                new Runnable() {
                    int runs = 0;

                    @Override
                    public void run() {
                        ClaimVisualization.beginFrameBudget();

                        Set<World> worlds = Bukkit.getOnlinePlayers()
                                .stream()
                                .map(Player::getWorld)
                                .collect(Collectors.toSet());

                        if (worlds.isEmpty()) {
                            stopGlobal();
                            return;
                        }

                        for (World w : worlds) {
                            ClaimVisualization.renderAllClaimsOnceForWorld(w);
                        }

                        runs++;
                        if (runs >= maxRuns) stopGlobal();
                    }
                },
                0L,
                periodTicks
        );

        context.msg(
                ChatColor.GREEN + "Visualisation des claims activée pendant "
                        + seconds + "s. "
                        + ChatColor.DARK_GRAY + "(/k vizclaims stop pour arrêter)"
        );
    }

    private void stopGlobal() {
        if (globalTaskId != null) {
            Bukkit.getScheduler().cancelTask(globalTaskId);
            globalTaskId = null;
        }
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "vizclaims " + ChatColor.WHITE + "[seconds|stop]";
    }
}
