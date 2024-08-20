package com.kingdomspvp.kingdoms.model;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Kingdom {
    private String name;

    private ChatColor color;
    private List<Faction> factions;
    private Faction defaultFaction;

    public Kingdom(String name, ChatColor color) {
        this.name = name;
        this.color = color;
        this.factions = new ArrayList<>();
        createDefaultFaction();
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    public List<Faction> getFactions() {
        return factions;
    }

    private void createDefaultFaction() {
        String factionName = "Paysans_" + name;
        if (!Factions.getInstance().isTagTaken(factionName)){
            defaultFaction = Factions.getInstance().createFaction();
            defaultFaction.setTag(factionName);
            defaultFaction.setDescription(ChatColor.GRAY + "Faction par défaut pour le royaume " + ChatColor.GREEN + name);
            factions.add(defaultFaction);
        } else {
            defaultFaction = Factions.getInstance().getByTag(factionName);
            factions.add(defaultFaction);
        };
    }

    public void addPlayerToDefaultFaction(Player player) {
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        fPlayer.setFaction(defaultFaction, true);
        // Send a message to the player indicating they have been added to the faction
        player.sendMessage(ChatColor.GRAY + "Vous avez été ajouté à la faction par défaut du royaume " + this.color + name);
    }

    public void addFaction(Faction faction) {
        factions.add(faction);
    };

}