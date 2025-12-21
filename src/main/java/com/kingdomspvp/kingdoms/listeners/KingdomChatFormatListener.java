package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class KingdomChatFormatListener implements Listener {

    private final LuckPerms luckPerms = LuckPermsProvider.get();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();

        // ----- ROYAUME -----
        Kingdom kingdom = KingdomsManager.getKingdomOfPlayer(p);
        ChatColor kingdomColor = (kingdom != null && kingdom.getColor() != null)
                ? kingdom.getColor()
                : ChatColor.WHITE;

        // ----- FACTION -----
        FPlayer fp = FPlayers.getInstance().getByPlayer(p);
        Faction faction = (fp != null) ? fp.getFaction() : null;
        String factionName = (faction != null && !faction.isWilderness())
                ? faction.getTag()
                : "SansFaction";

        // ----- GRADE -----
        User user = luckPerms.getUserManager().getUser(p.getUniqueId());
        String group = (user != null) ? user.getPrimaryGroup() : "roturier";
        GradeFormat grade = formatGrade(group);

        // ----- FORMAT FINAL -----
        String message =
                grade.prefix + " " +
                        kingdomColor + "[" + factionName + "] " +
                        grade.color + p.getName() +
                        ChatColor.GRAY + " : " +
                        ChatColor.WHITE + e.getMessage();

        e.setCancelled(true);
        e.getRecipients().forEach(r -> r.sendMessage(message));
    }

    // ===== FORMAT DES GRADES =====
    private static class GradeFormat {
        final String prefix;
        final ChatColor color;

        GradeFormat(String prefix, ChatColor color) {
            this.prefix = prefix;
            this.color = color;
        }
    }

    private GradeFormat formatGrade(String group) {
        return switch (group.toLowerCase()) {

            case "roturier" ->
                    new GradeFormat(ChatColor.GRAY + "[Roturier]", ChatColor.GRAY);

            case "bourgeois" ->
                    new GradeFormat(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "Bourgeois" + ChatColor.DARK_GRAY + "]",
                            ChatColor.AQUA);

            case "noble" ->
                    new GradeFormat(
                            ChatColor.RED + "[" + ChatColor.GOLD + "Noble" + ChatColor.RED + "]",
                            ChatColor.GOLD
                    );

            case "dignitaire" ->
                    new GradeFormat(
                            ChatColor.DARK_BLUE + "[" + ChatColor.DARK_AQUA + "Dignitaire" + ChatColor.DARK_BLUE + "]",
                            ChatColor.DARK_AQUA
                    );

            case "eminence" ->
                    new GradeFormat(
                            ChatColor.DARK_RED + "[" + ChatColor.DARK_PURPLE + "Eminence" + ChatColor.DARK_RED + "]",
                            ChatColor.DARK_PURPLE
                    );


            default ->
                    new GradeFormat(ChatColor.GRAY + "[Roturier]", ChatColor.GRAY);
        };
    }
}