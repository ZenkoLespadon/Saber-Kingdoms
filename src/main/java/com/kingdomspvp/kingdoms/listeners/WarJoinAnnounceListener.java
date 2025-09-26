// listeners/WarJoinAnnounceListener.java
package com.kingdomspvp.kingdoms.listeners;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.model.WarStatus;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.ChatUtil;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDateTime;
import java.util.UUID;

// TODO : Vérifier que quand tu te co après le commencement de la guerre ça te demande bien de te tp sans devoir deco reco
// TODO : Quand quelqu'un deco pendant une guerre ça casse tout et il faut qu'il se reco pour que ça remarche

public final class WarJoinAnnounceListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(
                com.massivecraft.factions.FactionsPlugin.getInstance(),
                () -> notifyIfWarRelevant(p),
                40L
        );
    }

    private void notifyIfWarRelevant(Player p) {
        if (p == null || !p.isOnline()) return;

        FPlayer fp = FPlayers.getInstance().getByPlayer(p);
        if (fp == null || fp.getFaction() == null || fp.getFaction().isWilderness()) return;

        Kingdom pk = KingdomsManager.getKingdomByFactionName(fp.getFaction().getTag());
        if (pk == null) return;

        UUID id = p.getUniqueId();
        LocalDateTime now = LocalDateTime.now();

        for (War w : WarManager.getWars().values()) {
            if (w.getStatus() == WarStatus.ENDED) continue;

            boolean sameSide = pk.equals(w.getAttackerKingdom()) || pk.equals(w.getDefenderKingdom());
            if (!sameSide) continue;

            boolean alreadyRegistered = w.getAttackerPlayers().contains(id) || w.getDefenderPlayers().contains(id);

            // --- Fenêtre d'inscription (REGISTRATION) ---
            if (w.getStatus() == WarStatus.REGISTRATION) {
                LocalDateTime start = w.getStartTime();
                LocalDateTime lead  = start.minus(WarManager.JOIN_PROMPT_LEAD_TIME);

                if (!now.isBefore(lead) && now.isBefore(start)) {
                    // n'envoyer le prompt que si NON inscrit
                    if (!alreadyRegistered) {
                        Kingdom opp = pk.equals(w.getAttackerKingdom()) ? w.getDefenderKingdom() : w.getAttackerKingdom();
                        String message = ChatColor.GOLD + "La guerre contre le royaume "
                                + ChatUtil.kingdomName(opp) + ChatColor.GOLD + " commence bientôt ! ";
                        BaseComponent[] prompt = WarManager.buildJoinPrompt(w, message);
                        p.spigot().sendMessage(prompt);
                    }
                }
                continue;
            }

            // --- Guerre en cours (INPROGRESS) ---
            if (w.getStatus() == WarStatus.INPROGRESS) {
                if (!alreadyRegistered) {
                    // Non inscrit → proposer l'inscription
                    Kingdom opp = pk.equals(w.getAttackerKingdom()) ? w.getDefenderKingdom() : w.getAttackerKingdom();
                    String message = ChatColor.GOLD + "Guerre en cours contre le Royaume "
                            + ChatUtil.kingdomName(opp) + ChatColor.GOLD + " ! ";
                    p.spigot().sendMessage(WarManager.buildJoinPrompt(w, message));
                } else {
                    // Déjà inscrit → pas de prompt d'inscription, seulement info utile (TP/wait)
                    if (w.hasCombatStarted()) {
                        p.spigot().sendMessage(WarManager.buildTpToWarNowMessage(w.getId()));
                    } else {
                        p.spigot().sendMessage(WarManager.buildWaitForAttackMessage(w.getDefenderKingdom()));
                    }
                }
            }
        }
    }
}
