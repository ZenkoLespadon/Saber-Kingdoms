package com.kingdomspvp.kingdoms.commands;

import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class KingdomCommandContext {
    public CommandSender sender;
    public List<String> args;
    public Player player;
    public FactionsPlugin plugin;

    public KingdomCommandContext(CommandSender sender, List<String> args, FactionsPlugin plugin) {
        this.sender = sender;
        this.args = new ArrayList<>(args); // Transforme la liste en une liste mutable
        this.plugin = plugin;
        if (sender instanceof Player) {
            this.player = (Player) sender;
        }
    }

    public void msg(String message) {
        sender.sendMessage(message);
    }
}
