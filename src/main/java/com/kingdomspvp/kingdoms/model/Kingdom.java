package com.kingdomspvp.kingdoms.model;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Kingdom {
    private String name;
    private ChatColor color;
    private List<String> factionIds;  // IDs des factions
    private String defaultFactionId;  // ID de la faction par défaut

    public Kingdom(String name, ChatColor color) {
        this.name = name;
        this.color = color;
        this.factionIds = new ArrayList<>();
        createDefaultFaction();
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    public List<Faction> getFactions() {
        return factionIds.stream()
                .map(id -> Factions.getInstance().getFactionById(id))
                .collect(Collectors.toList());
    }

    private void createDefaultFaction() {
        String factionName = "Paysans_" + name;
        Faction faction;
        if (!Factions.getInstance().isTagTaken(factionName)){
            faction = Factions.getInstance().createFaction();
            faction.setTag(factionName);
            faction.setDescription(ChatColor.GRAY + "Faction par défaut pour le royaume " + ChatColor.GREEN + name);
        } else {
            faction = Factions.getInstance().getByTag(factionName);
        }
        defaultFactionId = faction.getId();
        factionIds.add(defaultFactionId);
    }

    public void addPlayerToDefaultFaction(Player player) {
        Faction defaultFaction = Factions.getInstance().getFactionById(defaultFactionId);
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        fPlayer.setFaction(defaultFaction, true);
        // Envoie un message au joueur pour l'informer qu'il a été ajouté à la faction
        player.sendMessage(ChatColor.GRAY + "Vous avez été ajouté à la faction par défaut du royaume " + this.color + name);
    }

    public void addFaction(Faction faction) {
        factionIds.add(faction.getId());
    }

    public void removeFaction(Faction faction) {
        factionIds.remove(faction.getId());
    }

    public String getDefaultFactionId() {
        return defaultFactionId;
    }
}
