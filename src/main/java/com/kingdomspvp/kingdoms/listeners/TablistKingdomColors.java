// services/TablistKingdomColors.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

public final class TablistKingdomColors implements Listener {

    private final Plugin plugin;
    private int taskId = -1;

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

    public TablistKingdomColors(Plugin plugin, long periodTicks) {
        this.plugin = plugin;
        ensureTeamsExist();
        // premier refresh décalé d’1 tick pour laisser les autres listeners agir
        Bukkit.getScheduler().runTask(plugin, TablistKingdomColors::refreshAll);

        if (periodTicks > 0) {
            this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin, TablistKingdomColors::refreshAll, periodTicks, periodTicks
            );
        }
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // décale d’1 tick pour éviter chevauchements avec d’autres plugins
        Bukkit.getScheduler().runTask(plugin, () -> assign(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        safeRemoveFromCurrentTeam(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> assign(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> assign(e.getPlayer()));
    }

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

        Kingdom k = KingdomsManager.getKingdomOfPlayer(p);
        ChatColor c = (k != null && k.getColor() != null) ? k.getColor() : ChatColor.WHITE;

        Scoreboard s = sb();
        String targetName = teamNameFor(c);
        Team target = s.getTeam(targetName);
        if (target == null) target = s.registerNewTeam(targetName);

        String entry = p.getName();

        Team current = s.getEntryTeam(entry);
        if (current != null && current.getName().equals(target.getName())) {
            // déjà dans la bonne équipe → rien à faire
        } else {
            if (current != null && current.hasEntry(entry)) {
                current.removeEntry(entry); // un seul REMOVE_PLAYER, pas de boucle
            }
            if (!target.hasEntry(entry)) {
                target.addEntry(entry);
            }
        }

        String desiredListName = c + p.getName() + ChatColor.RESET;
        if (!desiredListName.equals(p.getPlayerListName())) {
            p.setPlayerListName(desiredListName);
        }
    }

    private static void safeRemoveFromCurrentTeam(Player p) {
        if (p == null) return;
        Scoreboard s = sb();
        Team current = s.getEntryTeam(p.getName());
        if (current != null && current.hasEntry(p.getName())) {
            current.removeEntry(p.getName());
        }
    }
}
