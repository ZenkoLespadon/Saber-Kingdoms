package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class SetKingdomCommand extends KingdomCommand {

    public SetKingdomCommand() {
        this.aliases = Arrays.asList("setkingdom", "sk");
        this.requiredArgs = Arrays.asList("player", "kingdom");
        this.helpShort = ChatColor.GRAY + "Met un joueur dans un royaume (admin)";
        this.permission = "kingdoms.admin.setkingdom";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        CommandSender sender = context.sender;

        if (!sender.hasPermission(this.permission)) {
            sender.sendMessage(ChatColor.RED + "Permission manquante: kingdoms.admin.setkingdom");
            return;
        }

        String targetName = context.args.get(0);
        String kingdomName = context.args.get(1);

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable ou hors-ligne: " + targetName);
            return;
        }

        Kingdom kingdom = context.plugin.getKingdomsManager().getKingdomByName(kingdomName);
        if (kingdom == null) {
            sender.sendMessage(ChatColor.RED + "Royaume introuvable: " + kingdomName);
            return;
        }

        // Si déjà dans la faction par défaut du royaume, inutile d'agir
        if (KingdomsManager.playerInDefaultFaction(target, kingdom)) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + ChatColor.GRAY + " est déjà dans le royaume "
                    + kingdom.getColor() + kingdom.getName());
            return;
        }

        // Quitter la faction actuelle proprement (si nécessaire)
        FPlayer fTarget = FPlayers.getInstance().getByPlayer(target);
        Faction current = fTarget.getFaction();
        // MassiveCraft gère le transfert dans setFaction(..., true), mais on peut notifier
        // (aucune suppression de rôle requise car setFaction(true) s’en charge)

        // Affectation à la faction par défaut du royaume
        KingdomsManager.addPlayerToDefaultFactionOfKingdom(target, kingdom);
        // (optionnel) créer le profil interne si votre système le requiert
        // KPlayerManager.createKPlayer(target);

        // Feedback
        target.sendMessage(ChatColor.GRAY + "Vous avez été placé dans le royaume : "
                + kingdom.getColor() + kingdom.getName());
        sender.sendMessage(ChatColor.GREEN + "OK. " + ChatColor.YELLOW + target.getName()
                + ChatColor.GRAY + " est maintenant dans " + kingdom.getColor() + kingdom.getName());
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "setkingdom " + ChatColor.WHITE + "<player> <kingdom>";
    }

    @Override
    public java.util.List<String> tabComplete(KingdomCommandContext context) {
        int n = context.args.size();
        if (n <= 0) return java.util.Collections.emptyList();

        // arg0 : joueur en ligne
        if (n == 1) {
            String pref = context.args.get(0).toLowerCase(java.util.Locale.ROOT);
            java.util.List<String> names = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names.stream()
                    .filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(pref))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        // arg1 : royaume
        if (n == 2) {
            String pref = context.args.get(1).toLowerCase(java.util.Locale.ROOT);
            return com.kingdomspvp.kingdoms.services.KingdomsManager.getKingdoms().stream()
                    .map(com.kingdomspvp.kingdoms.model.Kingdom::getName)
                    .filter(nm -> nm.toLowerCase(java.util.Locale.ROOT).startsWith(pref))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return java.util.Collections.emptyList();
    }

}

