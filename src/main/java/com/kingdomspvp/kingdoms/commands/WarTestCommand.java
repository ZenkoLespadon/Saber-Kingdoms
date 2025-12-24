package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import org.bukkit.ChatColor;

import java.util.Arrays;

public class WarTestCommand extends KingdomCommand {

    public WarTestCommand() {
        this.aliases = Arrays.asList("wartest");
        this.requiredArgs = Arrays.asList(); // on gère on|off|status nous-mêmes
        this.helpShort = ChatColor.GRAY + "Active/désactive le mode test (fenêtre 1–20 min).";
        this.optionalArgs.put("on|off|status", "");
        this.permission = "kingdoms.admin.war.testmode";
    }

    @Override
    public boolean validCall(KingdomCommandContext context) {
        // Autoriser 0 ou 1 arg
        if (context.args == null) return true;
        if (context.args.size() <= 1) return true;
        context.sender.sendMessage(getUsageTemplate());
        return false;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            ChatUtil.sendWarMsg(null, ChatColor.RED + "Commande réservée aux joueurs.");
            return;
        }
        String sub = context.args.isEmpty() ? "status" : context.args.get(0).toLowerCase();

        switch (sub) {
            case "on":
                WarManager.enableTestMode();
                ChatUtil.sendWarMsg(context.player, ChatColor.GREEN + "Mode test activé — fenêtre " +
                        ChatColor.AQUA + "1–20 min" + ChatColor.GREEN + ", prompt d'inscription " +
                        ChatColor.AQUA + "30s" + ChatColor.GREEN + ".");
                break;
            case "off":
                WarManager.disableTestMode();
                ChatUtil.sendWarMsg(context.player, ChatColor.YELLOW + "Mode test désactivé — fenêtre " +
                        ChatColor.AQUA + "1–24 h" + ChatColor.YELLOW + ", prompt " +
                        ChatColor.AQUA + "5 min" + ChatColor.YELLOW + ".");
                break;
            case "status":
            default:
                boolean on = WarManager.isTestMode();
                ChatUtil.sendWarMsg(context.player, (on ? ChatColor.GREEN : ChatColor.YELLOW) +
                        "Mode test: " + ChatColor.AQUA + (on ? "ON" : "OFF") + ChatColor.GRAY +
                        " — fenêtre actuelle: " + ChatColor.AQUA +
                        WarManager.MIN_TIME_BEFORE_WAR.toMinutes() + "–" +
                        WarManager.MAX_TIME_BEFORE_WAR.toMinutes() + " min");
                break;
        }
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "wartest " + ChatColor.WHITE + "[on|off|status]";
    }

    @Override
    public java.util.List<String> tabComplete(KingdomCommandContext context) {
        int n = context.args.size();
        if (n == 0) return java.util.Collections.emptyList();
        if (n == 1) {
            String pref = context.args.get(0).toLowerCase(java.util.Locale.ROOT);
            return java.util.Arrays.asList("on","off","status").stream()
                    .filter(s -> s.startsWith(pref))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return java.util.Collections.emptyList();
    }
}

