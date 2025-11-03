package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.WarManager;
import org.bukkit.ChatColor;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

public class WarsListCommand extends KingdomCommand {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public WarsListCommand() {
        this.aliases      = Arrays.asList("wars", "warslist");
        this.helpShort    = ChatColor.GRAY + "Affiche la liste des guerres programmées ou en cours";
        this.requiredArgs = Arrays.asList();
    }

    @Override
    public void perform(KingdomCommandContext context) {
        Map<String, War> wars = WarManager.getWars();
        if (wars.isEmpty()) {
            context.msg(ChatColor.YELLOW + "Aucune guerre n'est programmée ou en cours.");
            return;
        }

        context.msg("");
        context.msg(ChatColor.GOLD + "=== Liste des guerres ===");
        for (War w : wars.values()) {
            String attacker = w.getAttackerKingdom().getColor() + w.getAttackerKingdom().getName();
            String defender = w.getDefenderKingdom().getColor() + w.getDefenderKingdom().getName();
            String date     = w.getStartTime().format(DATE_FMT);
            String time     = w.getStartTime().format(TIME_FMT);

            context.msg(
                    ChatColor.GREEN  + attacker +
                            ChatColor.WHITE  + " vs " +
                            ChatColor.RED    + defender +
                            ChatColor.GRAY   + " le " +
                            ChatColor.AQUA   + date +
                            ChatColor.GRAY   + " à " +
                            ChatColor.AQUA   + time
            );
        }
        context.msg(ChatColor.GOLD + "========================");
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "wars";
    }
}
