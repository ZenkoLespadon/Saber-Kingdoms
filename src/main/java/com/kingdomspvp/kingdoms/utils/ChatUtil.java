package com.kingdomspvp.kingdoms.utils;

import com.kingdomspvp.kingdoms.model.Kingdom;

/** Utilitaires chat autour des royaumes. */
public final class ChatUtil {
    private ChatUtil() {}

    /** Retourne le nom du royaume préfixé par sa couleur Bukkit (legacy). */
    public static String kingdomName(Kingdom k) {
        if (k == null) return "???";
        org.bukkit.ChatColor c = k.getColor();
        String name = k.getName() != null ? k.getName() : "???";
        return (c != null ? c.toString() : "") + name + org.bukkit.ChatColor.RESET;
    }
}

