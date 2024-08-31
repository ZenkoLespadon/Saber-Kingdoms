package com.kingdomspvp.kingdoms.model;

import org.bukkit.entity.Player;

public class KPlayer {

    Player player;
    Integer power;

    public KPlayer(Player player, Integer power) {
        this.player = player;
        this.power = power;
    }

    public KPlayer(Player player) {
        this.player = player;
        this.power = 0;
    }

    public Player getPlayer() {
        return player;
    }

    public Integer getPower() {
        return power;
    }

    public void setPower(Integer power) {
        this.power = power;
    }

    public void addPower(Integer power) {
        this.power += power;
    }
}
