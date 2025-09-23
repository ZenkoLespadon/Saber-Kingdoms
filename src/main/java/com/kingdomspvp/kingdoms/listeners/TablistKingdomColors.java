// services/TablistKingdomColors.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

public final class TablistKingdomColors implements Listener {

    private final Plugin plugin;
    private int taskId = -1;

    // Ordre de tri voulu dans le TAB
    private static final ChatColor[] ORDER = new ChatColor[] {
            ChatColor.DARK_RED, ChatColor.RED,
            ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.DARK_GREEN, ChatColor.GREEN,
            ChatColor.DARK_AQUA, ChatColor.AQUA,
            ChatColor.DARK_BLUE, ChatColor.BLUE,
            ChatColor.DARK_PURPLE, ChatColor.LIGHT_PURPLE,
            ChatColor.GRAY, ChatColor.DARK_GRAY,
            ChatColor.WHITE, ChatColor.BLACK
    };
    private static final Map<ChatColor, String> NAME_CACHE = new HashMap<>();

    private static Scoreboard sb() { return Bukkit.getScoreboardManager().getMainScoreboard(); }

    /** Enregistre le listener et démarre l’auto-refresh (période ticks). */
    public TablistKingdomColors(Plugin plugin, long periodTicks) {
        this.plugin = plugin;
        ensureTeamsExist();
        // assign immédiat sur les joueurs déjà connectés
        refreshAll();

        // tâche périodique
        if (periodTicks > 0) {
            this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin, TablistKingdomColors::refreshAll, periodTicks, periodTicks
            );
        }
    }

    /** Stoppe l’auto-refresh (à appeler dans onDisable). */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    // --- Hooks évènementiels pour rafraîchir plus souvent ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { assign(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { removeFromAnyTeam(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) { assign(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) { assign(e.getPlayer()); }

    // --- API statique ---
    public static void refresh(Player p) { assign(p); }

    public static void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) assign(p);
    }

    // ---------- internes ----------
    private static void ensureTeamsExist() {
        Scoreboard s = sb();
        for (int i = 0; i < ORDER.length; i++) {
            ChatColor c = ORDER[i];
            String name = teamNameFor(c);
            Team t = s.getTeam(name);
            if (t == null) t = s.registerNewTeam(name);
            t.setColor(c);
            t.setPrefix(c.toString());
            t.setSuffix("");
        }
    }

    private static String teamNameFor(ChatColor c) {
        return NAME_CACHE.computeIfAbsent(c, cc -> {
            int idx = 99;
            for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == cc) { idx = i; break; }
            return String.format("KC_%02d_%s", idx, cc.name());
        });
    }

    private static void assign(Player p) {
        if (p == null || !p.isOnline()) return;

        removeFromAnyTeam(p);

        Kingdom k = getPlayerKingdom(p);
        ChatColor c = (k != null && k.getColor() != null) ? k.getColor() : ChatColor.WHITE;

        Team t = sb().getTeam(teamNameFor(c));
        if (t != null) {
            t.addEntry(p.getName());
            p.setPlayerListName(c + p.getName() + ChatColor.RESET);
        }
    }

    private static void removeFromAnyTeam(Player p) {
        Scoreboard s = sb();
        for (Team t : s.getTeams()) {
            if (t.hasEntry(p.getName())) t.removeEntry(p.getName());
        }
    }

    private static Kingdom getPlayerKingdom(Player p) {
        FPlayer fp = FPlayers.getInstance().getByPlayer(p);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) return null;
        return KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
    }
}
