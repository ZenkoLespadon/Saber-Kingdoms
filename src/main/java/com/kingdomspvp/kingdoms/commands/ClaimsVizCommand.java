// src/main/java/com/kingdomspvp/kingdoms/commands/ClaimsVizCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.utils.ClaimVisualization;
import com.kingdomspvp.kingdoms.utils.PlayerUtil;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimsVizCommand extends KingdomCommand {

    // --- Runtime settings (reloadables) ---
    private static int MIN_SECONDS;
    private static int MAX_SECONDS;
    private static int DEFAULT_SECONDS;
    private static int PERIOD_TICKS;

    // --- Task globale ---
    private static Integer globalTaskId = null;

    private final FactionsPlugin plugin;

    public ClaimsVizCommand(FactionsPlugin plugin) {
        this.plugin = plugin;

        ClaimVisualization.init(plugin);
        reloadSettings();

        this.aliases = Arrays.asList("vizclaims", "vizall");
        this.requiredArgs = Collections.emptyList();
        this.helpShort = ChatColor.GRAY + "Trace tous les claims via particules (visible par tous).";
        this.optionalArgs.put("seconds", "");
    }

    // =====================================================
    // Reload
    // =====================================================

    public static void reloadSettings() {
        var cfg = SettingsProvider.get().commands().vizclaims();
        MIN_SECONDS = cfg.minSeconds();
        MAX_SECONDS = cfg.maxSeconds();
        DEFAULT_SECONDS = cfg.defaultSeconds();
        PERIOD_TICKS = cfg.periodTicks();
    }

    // =====================================================
    // Command
    // =====================================================

    @Override
    public void perform(KingdomCommandContext context) {

        if (context.player == null) {
            context.msg(ChatColor.RED + "Uniquement en jeu.");
            return;
        }

        if (!PlayerUtil.isInOverworld(context.player)) {
            context.player.sendMessage(ChatColor.RED + "Cette commande ne peut être exécutée que dans l'Overworld.");
            return;
        }

        // ---- STOP ----
        if (!context.args.isEmpty() && "stop".equalsIgnoreCase(context.args.get(0)) && context.sender.hasPermission(this.permission)) {
            stopGlobal();
            context.msg(ChatColor.YELLOW + "Visualisation arrêtée pour tous.");
            return;
        }

        reloadSettings();

        int seconds = DEFAULT_SECONDS;

        // ---- ARGUMENT SECONDS ----
        if (!context.args.isEmpty()) {
            String arg = context.args.get(0);

            int parsed;
            try {
                parsed = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                context.msg(ChatColor.RED + "Valeur invalide : '" + arg + "'");
                context.msg(ChatColor.GRAY + "Utilisation : /k vizclaims <seconds>");
                context.msg(ChatColor.GRAY + "Plage autorisée : " + MIN_SECONDS + " à " + MAX_SECONDS + " secondes.");
                return;
            }

            if (parsed < MIN_SECONDS) {
                context.msg(ChatColor.RED + "Durée trop courte : " + parsed + "s");
                context.msg(ChatColor.GRAY + "Minimum autorisé : " + MIN_SECONDS + " secondes.");
                return;
            }

            if (parsed > MAX_SECONDS) {
                context.msg(ChatColor.RED + "Durée trop longue : " + parsed + "s");
                context.msg(ChatColor.GRAY + "Maximum autorisé : " + MAX_SECONDS + " secondes.");
                return;
            }

            seconds = parsed;
        }

        // ---- LANCEMENT ----
        stopGlobal();

        final int ticksPerSecond = 20;
        final int maxRuns = (seconds * ticksPerSecond) / PERIOD_TICKS;

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
                PERIOD_TICKS
        );

        context.msg(
                ChatColor.GREEN + "Visualisation des claims activée pendant "
                        + seconds + "s. "
                        + ChatColor.DARK_GRAY + "(/k vizclaims stop pour arrêter)"
        );
    }

    // =====================================================
    // Utils
    // =====================================================

    private static void stopGlobal() {
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
