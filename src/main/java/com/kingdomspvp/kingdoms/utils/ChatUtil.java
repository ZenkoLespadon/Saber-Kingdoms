package com.kingdomspvp.kingdoms.utils;

import com.kingdomspvp.kingdoms.model.Kingdom;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class ChatUtil {
    private ChatUtil() {}

    private static final String PREFIX = ChatColor.WHITE + "[" + ChatColor.GREEN + "War" + ChatColor.WHITE + "] ";

    /** Retourne le nom du royaume préfixé par sa couleur Bukkit (legacy). */
    public static String kingdomName(Kingdom k) {
        if (k == null) return "???";
        ChatColor c = k.getColor();
        String name = k.getName() != null ? k.getName() : "???";
        return (c != null ? c.toString() : "") + name + ChatColor.RESET;
    }

    /** Ajoute le préfixe [War] et envoie au joueur. */
    public static void sendWarMsg(Player p, String msg) {
        if (p != null) {
            p.sendMessage(PREFIX + msg);
        }
    }

    /** Ajoute juste le préfixe [War] et retourne la chaîne. */
    public static String prefixWithWar(String msg) {
        return PREFIX + msg;
    }
}
