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

        // ----- FORMAT DU NOM DU JOUEUR -----
        String nameFormat = grade.color.toString();
        if (grade.bold) {
            nameFormat += ChatColor.BOLD;
        }
        nameFormat += p.getName();

        // ----- FORMAT FINAL -----
        String message =
                grade.prefix + ChatColor.RESET + " " +
                        kingdomColor + "[" + factionName + "] " +
                        nameFormat +
                        ChatColor.GRAY + " : " +
                        grade.messageColor + e.getMessage();

        e.setCancelled(true);
        e.getRecipients().forEach(r -> r.sendMessage(message));

    }

    private static class GradeFormat {
        final String prefix;
        final ChatColor color;
        final ChatColor messageColor;
        final boolean bold;

        GradeFormat(String prefix, ChatColor color, ChatColor messageColor, boolean bold) {
            this.prefix = prefix;
            this.color = color;
            this.messageColor = messageColor;
            this.bold = bold;
        }

        GradeFormat(String prefix, ChatColor color) {
            this(prefix, color, ChatColor.WHITE, false);
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

            case "helper" ->
                    new GradeFormat(
                            ChatColor.LIGHT_PURPLE + "[Helper]",
                            ChatColor.LIGHT_PURPLE
                    );

            case "moderateur" ->
                    new GradeFormat(
                            ChatColor.DARK_AQUA + "[Modérateur]",
                            ChatColor.DARK_AQUA
                    );

            case "moderateur+" -> new GradeFormat(
                    ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "[" +
                            ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Modérateur+" +
                            ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "]",
                    ChatColor.DARK_AQUA,
                    ChatColor.AQUA,
                    true
            );


            case "builder" ->
                    new GradeFormat(
                            ChatColor.DARK_GREEN + "[Builder]",
                            ChatColor.DARK_GREEN
                    );

            case "developpeur" ->
                    new GradeFormat(
                            ChatColor.WHITE + "[" + ChatColor.DARK_RED + "Développeur" + ChatColor.WHITE + "]",
                            ChatColor.DARK_RED
                    );

            case "administrateur" -> new GradeFormat(
                    ChatColor.BLACK + "" + ChatColor.BOLD + "[" +
                            ChatColor.DARK_RED + "" + ChatColor.BOLD + "Administrateur" +
                            ChatColor.BLACK + "" + ChatColor.BOLD + "]",
                    ChatColor.DARK_RED,
                    ChatColor.RED,
                    true
            );

            default ->
                    new GradeFormat(ChatColor.GRAY + "[Roturier]", ChatColor.GRAY);
        };
    }
}