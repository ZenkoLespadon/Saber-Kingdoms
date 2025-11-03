// commands/WarListCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /k warlist [all|upcoming|inprogress|ended]
 * Liste les guerres : à venir, en cours, passées (admin).
 */
public final class WarsListAdminCommand extends KingdomCommand {

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public WarsListAdminCommand() {
        this.aliases      = Arrays.asList("warsadminlist");
        this.requiredArgs = Collections.emptyList();
        this.helpShort    = ChatColor.GRAY + "Liste les guerres à venir, en cours et terminées (admin).";
        this.optionalArgs.put("filtre", ChatColor.DARK_GRAY + " all|upcoming|inprogress|ended");
        this.permission   = "kingdoms.admin.war.warsadminlist";
    }

    @Override
    public boolean validCall(KingdomCommandContext context) {
        CommandSender s = context.sender;
        if (!s.hasPermission(this.permission)) {
            s.sendMessage(ChatUtil.prefixWithWar(ChatColor.RED + "Permission insuffisante."));
            return false;
        }
        // 0 ou 1 argument (filtre)
        if (context.args == null || context.args.size() <= 1) return true;
        s.sendMessage(getUsageTemplate());
        return false;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        String filter = (context.args.isEmpty() ? "all" : context.args.get(0)).toLowerCase(Locale.ROOT);

        // Snapshot des guerres
        Map<String, War> all = new HashMap<>(WarManager.getWars());
        LocalDateTime now = LocalDateTime.now();

        // Groupes
        List<War> upcoming = all.values().stream()
                .filter(w -> w.getStatus() == WarStatus.REGISTRATION && w.getStartTime().isAfter(now))
                .sorted(Comparator.comparing(War::getStartTime))
                .collect(Collectors.toList());

        List<War> live = all.values().stream()
                .filter(w -> w.getStatus() == WarStatus.INPROGRESS)
                .sorted(Comparator.comparing(War::getStartTime))
                .collect(Collectors.toList());

        List<War> ended = all.values().stream()
                .filter(w -> w.getStatus() == WarStatus.ENDED || (w.getStatus() == WarStatus.REGISTRATION && !w.getStartTime().isAfter(now)))
                .sorted(Comparator.comparing(War::getStartTime).reversed())
                .collect(Collectors.toList());

        // Affichage
        switch (filter) {
            case "upcoming":
                sendHeader(context, "Guerres à venir", upcoming.size());
                if (upcoming.isEmpty()) sendLine(context, ChatColor.DARK_GRAY + "Aucune.");
                else upcoming.forEach(w -> sendLine(context, formatUpcoming(w)));
                break;

            case "inprogress":
            case "live":
                sendHeader(context, "Guerres en cours", live.size());
                if (live.isEmpty()) sendLine(context, ChatColor.DARK_GRAY + "Aucune.");
                else live.forEach(w -> sendLine(context, formatLive(w)));
                break;

            case "ended":
            case "past":
                sendHeader(context, "Guerres terminées", ended.size());
                if (ended.isEmpty()) sendLine(context, ChatColor.DARK_GRAY + "Aucune.");
                else ended.forEach(w -> sendLine(context, formatEnded(w)));
                break;

            case "all":
            default:
                sendHeader(context, "Guerres en cours", live.size());
                if (live.isEmpty()) sendLine(context, ChatColor.DARK_GRAY + "Aucune.");
                else live.forEach(w -> sendLine(context, formatLive(w)));

                sendHeader(context, "Guerres à venir", upcoming.size());
                if (upcoming.isEmpty()) sendLine(context, ChatColor.DARK_GRAY + "Aucune.");
                else upcoming.forEach(w -> sendLine(context, formatUpcoming(w)));

                sendHeader(context, "Guerres terminées", ended.size());
                if (ended.isEmpty()) sendLine(context, ChatColor.DARK_GRAY + "Aucune.");
                else ended.stream().limit(20).forEach(w -> sendLine(context, formatEnded(w))); // limite affichage
                break;
        }
    }

    private static void sendHeader(KingdomCommandContext ctx, String title, int count) {
        ctx.sender.sendMessage(ChatUtil.prefixWithWar(
                ChatColor.GOLD + "— " + ChatColor.YELLOW + title + ChatColor.GRAY + " (" + count + ")" + ChatColor.GOLD + " —"
        ));
    }

    private static void sendLine(KingdomCommandContext ctx, String line) {
        ctx.sender.sendMessage(ChatUtil.prefixWithWar(line));
    }

    private static String formatUpcoming(War w) {
        String atk = com.kingdomspvp.kingdoms.utils.ChatUtil.kingdomName(w.getAttackerKingdom());
        String def = com.kingdomspvp.kingdoms.utils.ChatUtil.kingdomName(w.getDefenderKingdom());
        return ChatColor.AQUA + "#" + w.getId() + ChatColor.GRAY + " | "
                + atk + ChatColor.GRAY + " vs " + def + ChatColor.GRAY + " — "
                + ChatColor.WHITE + w.getStartTime().format(FR) + ChatColor.GRAY + " (à venir)";
    }

    private static String formatLive(War w) {
        String atk = com.kingdomspvp.kingdoms.utils.ChatUtil.kingdomName(w.getAttackerKingdom());
        String def = com.kingdomspvp.kingdoms.utils.ChatUtil.kingdomName(w.getDefenderKingdom());
        String phase = w.hasCombatStarted() ? (ChatColor.RED + "COMBAT") : (ChatColor.YELLOW + "Détection");
        return ChatColor.AQUA + "#" + w.getId() + ChatColor.GRAY + " | "
                + atk + ChatColor.GRAY + " vs " + def + ChatColor.GRAY + " — "
                + ChatColor.GREEN + "début " + ChatColor.WHITE + w.getStartTime().format(FR)
                + ChatColor.GRAY + " | " + phase;
    }

    // Java
    private static String formatEnded(War w) {
        String atk = com.kingdomspvp.kingdoms.utils.ChatUtil.kingdomName(w.getAttackerKingdom());
        String def = com.kingdomspvp.kingdoms.utils.ChatUtil.kingdomName(w.getDefenderKingdom());
        String atkDisplay = atk;
        String defDisplay = def;

        // Utilise le champ winner pour souligner le gagnant
        if (w.getWinner() != null) {
            if (w.getWinner().equals(w.getAttackerKingdom())) {
                atkDisplay = ChatColor.UNDERLINE + atk + ChatColor.RESET;
            } else if (w.getWinner().equals(w.getDefenderKingdom())) {
                defDisplay = ChatColor.UNDERLINE + def + ChatColor.RESET;
            }
        }

        return ChatColor.AQUA + "#" + w.getId() + ChatColor.GRAY + " | "
                + atkDisplay + ChatColor.GRAY + " vs " + defDisplay + ChatColor.GRAY + " — "
                + ChatColor.DARK_GRAY + "terminée (début " + w.getStartTime().format(FR) + ")";
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "warlist " + ChatColor.WHITE + "[all|upcoming|inprogress|ended]";
    }
}
