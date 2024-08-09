package com.kingdomspvp.kingdoms.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String playerName = player.getName(); // Récupérer le pseudo du joueur
            player.sendMessage("Votre pseudo est : " + playerName);
            return true;
        }
        sender.sendMessage("Cette commande ne peut être exécutée que par un joueur.");
        return false;
    }
}
