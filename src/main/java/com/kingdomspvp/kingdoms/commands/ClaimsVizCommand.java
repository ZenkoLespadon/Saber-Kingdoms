// src/main/java/com/kingdomspvp/kingdoms/commands/ClaimsVizCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.utils.ClaimVisualization;
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

        if (!context.args.isEmpty() && "stop".equalsIgnoreCase(context.args.get(0))) {
            stopGlobal();
            context.msg(ChatColor.YELLOW + "Visualisation arrêtée pour tous.");
            return;
        }

        int seconds = 30;
        if (!context.args.isEmpty()) {
            try { seconds = Math.max(1, Integer.parseInt(context.args.get(0))); }
            catch (NumberFormatException ignored) {}
        }

        stopGlobal();

        final int periodTicks = 10; // 0.5s
        final int maxRuns = (seconds * 20) / periodTicks;

        globalTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                new Runnable() {
                    int runs = 0;
                    @Override public void run() {
                        ClaimVisualization.beginFrameBudget(); // reset budget/compteurs

                        Set<World> worlds = Bukkit.getOnlinePlayers()
                                .stream().map(Player::getWorld).collect(Collectors.toSet());

                        if (worlds.isEmpty()) { stopGlobal(); return; }

                        for (World w : worlds) {
                            ClaimVisualization.renderAllClaimsOnceForWorld(w);
                        }

                        runs++;
                        if (runs >= maxRuns) stopGlobal();
                    }
                },
                0L, periodTicks
        );

        context.msg(ChatColor.GREEN + "Visualisation des claims pour tous pendant " + seconds + "s. " +
                ChatColor.DARK_GRAY + "(utilise '/k vizclaims stop' pour couper)");
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
