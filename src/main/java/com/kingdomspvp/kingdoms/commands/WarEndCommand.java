// commands/WarEndCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class WarEndCommand extends KingdomCommand {

    public WarEndCommand() {
        this.aliases      = Arrays.asList("warend", "endwar", "stopwar");
        this.requiredArgs = Arrays.asList("warId", "gagnant");
        this.helpShort    = ChatColor.GRAY + "Force l'arrêt d'une guerre en donnant la victoire au camp choisi.";
        this.permission   = "kingdoms.admin.war.end";
    }

    @Override
    public boolean validCall(KingdomCommandContext context) {
        // Admin only
        CommandSender s = context.sender;
        if (s instanceof Player) {
            if (!s.hasPermission(this.permission)) {
                s.sendMessage(ChatUtil.prefixWithWar(ChatColor.RED + "Permission insuffisante."));
                return false;
            }
        }
        // Besoin exactement 2 args
        if (context.args == null || context.args.size() != 2) {
            context.sender.sendMessage(getUsageTemplate());
            return false;
        }
        return true;
    }

    @Override
    public void perform(KingdomCommandContext context) {
        String warId = context.args.get(0);
        String winnerArg = context.args.get(1);

        War war = WarManager.getWar(warId);
        if (war == null) {
            context.sender.sendMessage(ChatUtil.prefixWithWar(ChatColor.RED + "Guerre introuvable (ID=" + warId + ")."));
            return;
        }
        if (war.getStatus() == WarStatus.ENDED) {
            context.sender.sendMessage(ChatUtil.prefixWithWar(ChatColor.RED + "Cette guerre est déjà terminée."));
            return;
        }

        Boolean attackersWin = parseWinner(winnerArg, war);
        if (attackersWin == null) {
            String atk = war.getAttackerKingdom().getName();
            String def = war.getDefenderKingdom().getName();
            context.sender.sendMessage(ChatUtil.prefixWithWar(
                    ChatColor.RED + "Gagnant invalide. Utilisez " + ChatColor.YELLOW + "attaquants/defenseurs" +
                            ChatColor.RED + " ou le nom du royaume (" + ChatColor.YELLOW + atk + ChatColor.RED + " / " + ChatColor.YELLOW + def + ChatColor.RED + ")."
            ));
            return;
        }

        boolean ok = WarRuntime.forceEnd(warId, attackersWin);
        if (!ok) {
            context.sender.sendMessage(ChatUtil.prefixWithWar(ChatColor.RED + "Échec de l'arrêt forcé de la guerre."));
            return;
        }

        String winText = attackersWin
                ? (ChatColor.GREEN + "Victoire des attaquants (" + war.getAttackerKingdom().getName() + ")")
                : (ChatColor.RED   + "Victoire des défenseurs (" + war.getDefenderKingdom().getName() + ")");
        context.sender.sendMessage(ChatUtil.prefixWithWar(ChatColor.GOLD + "Guerre " + warId + " arrêtée. " + winText + ChatColor.GOLD + "."));
    }

    private static Boolean parseWinner(String arg, War war) {
        String a = arg.toLowerCase();

        if (a.startsWith("att") || a.startsWith("atk") || a.startsWith("attaq")) return true;
        if (a.startsWith("def") || a.startsWith("déf") || a.startsWith("defe")) return false;

        Kingdom atk = war.getAttackerKingdom();
        Kingdom def = war.getDefenderKingdom();
        if (atk != null && a.equalsIgnoreCase(atk.getName())) return true;
        if (def != null && a.equalsIgnoreCase(def.getName())) return false;

        return null;
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "warend " + ChatColor.WHITE + "<warId> <attaquants|défenseurs|NomRoyaume>";
    }

    @Override
    public java.util.List<String> tabComplete(KingdomCommandContext context) {
        int n = context.args.size();
        if (n <= 0) return java.util.Collections.emptyList();

        // arg0 : id de guerre
        if (n == 1) {
            String pref = context.args.get(0).toLowerCase(java.util.Locale.ROOT);
            return com.kingdomspvp.kingdoms.services.WarManager.getWars().keySet().stream()
                    .filter(id -> id != null && id.toLowerCase(java.util.Locale.ROOT).startsWith(pref))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        // arg1 : gagnant (attaquants|défenseurs|NomRoyaume)
        if (n == 2) {
            String pref = context.args.get(1).toLowerCase(java.util.Locale.ROOT);

            java.util.List<String> base = java.util.Arrays.asList(
                    "attaquants", "defenseurs", "défenseurs"
            );

            // si l’ID est valide, on ajoute les 2 noms de royaumes pour aider
            String warId = context.args.get(0);
            com.kingdomspvp.kingdoms.model.War w = com.kingdomspvp.kingdoms.services.WarManager.getWar(warId);
            java.util.List<String> all = new java.util.ArrayList<>(base);
            if (w != null) {
                if (w.getAttackerKingdom() != null) all.add(w.getAttackerKingdom().getName());
                if (w.getDefenderKingdom() != null) all.add(w.getDefenderKingdom().getName());
            }

            return all.stream()
                    .filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(pref))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return java.util.Collections.emptyList();
    }

}
