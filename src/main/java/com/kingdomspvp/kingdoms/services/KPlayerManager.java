package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.KPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class KPlayerManager {

    //TODO On a pas de KPlayer qui est créé
    private static TreeSet<KPlayer> sortedKPlayers = new TreeSet<>(Comparator.comparingInt(KPlayer::getPower).reversed());

    public static List<KPlayer> getSortedKPlayers() {
        return new ArrayList<>(sortedKPlayers);
    }

    public static void createKPlayer(Player player) {
        KPlayer kPlayer = new KPlayer(player, 0);
        addKPlayer(kPlayer);
    }

    public static KPlayer getKPlayerOfPlayer(Player player) {
        return sortedKPlayers.stream()
                .filter(kPlayer -> kPlayer.getPlayer().equals(player))
                .findFirst()
                .orElse(null);
    }

    public static void addKPlayer(KPlayer kPlayer) {
        if (!kPlayerInKPlayers(kPlayer.getPlayer())) {
            sortedKPlayers.add(kPlayer);
        }
    }

    public static void removeKPlayer(KPlayer kPlayer) {
        sortedKPlayers.remove(kPlayer);
    }

    public static void addPowerToKPlayer(KPlayer kPlayer, Integer power) {
        updatePlayerPower(kPlayer, kPlayer.getPower() + power);
    }

    public static void removePowerToKPlayer(KPlayer kPlayer, Integer power) {
        updatePlayerPower(kPlayer, kPlayer.getPower() - power);
    }

    public static Integer getPowerOfKPlayer(KPlayer kPlayer) {
        return kPlayer.getPower();
    }

    public static void setPowerOfKPlayer(KPlayer kPlayer, Integer power) {
        updatePlayerPower(kPlayer, power);
    }

    private static void updatePlayerPower(KPlayer kPlayer, int newPower) {
        // Retirer le joueur de l'ensemble trié avant la mise à jour du power
        sortedKPlayers.remove(kPlayer);
        // Mettre à jour le power
        kPlayer.setPower(newPower);
        // Réinsérer le joueur dans l'ensemble trié
        sortedKPlayers.add(kPlayer);
    }

    public static boolean kPlayerInKPlayers(Player player) {
        return sortedKPlayers.stream().anyMatch(kPlayer -> kPlayer.getPlayer().equals(player));
    }
}
