package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.KPlayer;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KPlayerManager {

    static List<KPlayer> kPlayers = new ArrayList<>();

    public static List<KPlayer> getKPlayers() {
        return kPlayers;
    }

    public static void createKPlayer(Player player) {
        if (!kPlayerInKPlayers(player)) {
            KPlayer kPlayer = new KPlayer(player);
            kPlayers.add(kPlayer);
        }
    }

    public static KPlayer getKPlayerOfPlayer(Player player) {
        return kPlayers.stream()
                .filter(kPlayer -> kPlayer.getPlayer().equals(player))
                .findFirst()
                .orElse(null);
    }


    public static void addKPlayer(KPlayer kPlayer) {
        if (!kPlayerInKPlayers(kPlayer.getPlayer())) {
            kPlayers.add(kPlayer);
        }
    }

    public static void addPowerToKPlayer(KPlayer kPlayer, Integer power) {
        kPlayer.addPower(power);
    }

    public static void removePowerToKPlayer(KPlayer kPlayer, Integer power) {
        kPlayer.addPower(-power);
    }

    public static Integer getPowerOfKPlayer(KPlayer kPlayer) {
        return kPlayer.getPower();
    }

    public static void setPowerOfKPlayer(KPlayer kPlayer, Integer power) {
        kPlayer.setPower(power);
    }

    public static boolean kPlayerInKPlayers(Player player) {
        return kPlayers.stream().anyMatch(kPlayer -> kPlayer.getPlayer().equals(player));
    }
}
