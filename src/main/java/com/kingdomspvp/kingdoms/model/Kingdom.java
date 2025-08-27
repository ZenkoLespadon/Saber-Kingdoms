package com.kingdomspvp.kingdoms.model;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// Ajoute en haut
import org.bukkit.Location;

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

    // Ajoute dans la classe Kingdom
    private Location spawnPoint;

    public List<Faction> getFactions() {
        return factionIds.stream()
                .map(id -> Factions.getInstance().getFactionById(id))
                .collect(Collectors.toList());
    }

    public Location getSpawnPoint() { return spawnPoint; }

    public void setSpawnPoint(Location loc) { this.spawnPoint = loc; }

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

    public void addFaction(Faction faction) {
        factionIds.add(faction.getId());
    }

    public void removeFaction(Faction faction) {
        factionIds.remove(faction.getId());
    }

    public String getDefaultFactionId() {
        return defaultFactionId;
    }

    public void setDefaultFactionId(String defaultFactionId) {
        this.defaultFactionId = defaultFactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Kingdom)) return false;
        Kingdom other = (Kingdom) o;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
