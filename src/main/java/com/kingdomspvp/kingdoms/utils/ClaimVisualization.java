package com.kingdomspvp.kingdoms.utils;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ClaimVisualization {

    private ClaimVisualization() {}

    // Réglages visuels/perf
    private static final float  DUST_SIZE     = 5.0f; // demandé
    private static final int    STEP          = 4;    // pas entre particules
    private static final long   PERIOD_TICKS  = 10L;  // 0,5 s
    private static final int    COUNT_PER_SPAWN = 1;  // 1 particule, mais grosse (size=5)

    // Un taskId par guerre
    private static final Map<String, Integer> WAR_TASKS = new ConcurrentHashMap<>();

    // === API publique appelée par WarManager ===

    /** Au début de la guerre (avant 1er claim attaqué) : afficher TOUS les claims pour tous les joueurs. */
    public static void startWarOutlines(War war) {
        stopWarOutlines(war); // évite doublons

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                getPlugin(),
                () -> {
                    // Mondes avec des joueurs connectés (on évite d'envoyer dans des mondes vides)
                    Set<World> worlds = Bukkit.getOnlinePlayers()
                            .stream().map(Player::getWorld).collect(Collectors.toSet());
                    if (worlds.isEmpty()) return;

                    for (World w : worlds) {
                        renderAllClaimsOnceForWorld(w);
                    }
                },
                0L, PERIOD_TICKS
        );
        WAR_TASKS.put(war.getId(), taskId);
    }

    /** Après 1er claim attaqué : n’afficher que ce claim (pour tous les joueurs). */
    public static void switchToAttackedClaimOnly(War war, Claim attackedClaim) {
        stopWarOutlines(war);
        int gx = attackedClaim.getGridX();
        int gz = attackedClaim.getGridZ();

        // Couleur : on prend celle du royaume propriétaire du claim attaqué (défenseur)
        ChatColor chat = ChatColor.GRAY;
        Kingdom k = KingdomsManager.getKingdomByName(attackedClaim.getKingdomName());
        if (k != null) chat = k.getColor();
        final Color color = chatToColor(chat);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                getPlugin(),
                () -> {
                    Set<World> worlds = Bukkit.getOnlinePlayers()
                            .stream().map(Player::getWorld).collect(Collectors.toSet());
                    if (worlds.isEmpty()) return;

                    for (World w : worlds) {
                        drawClaimOutlineForWorld(w, gx, gz, color);
                    }
                },
                0L, PERIOD_TICKS
        );
        WAR_TASKS.put(war.getId(), taskId);
    }

    /** À la fin de la guerre : plus d’affichage. */
    public static void stopWarOutlines(War war) {
        Integer id = WAR_TASKS.remove(war.getId());
        if (id != null) Bukkit.getScheduler().cancelTask(id);
    }

    // === Rendu de base ===

    /** Trace l’outline d’un claim dans un monde, visible par tous. Altitude = highest + 2. */
    public static void drawClaimOutlineForWorld(World world, int gridX, int gridZ, Color color) {
        int size = ClaimManager.CLAIM_SIZE; // 128
        int minX = gridX * size;
        int minZ = gridZ * size;
        int maxX = minX + size;
        int maxZ = minZ + size;

        Particle.DustOptions dust = new Particle.DustOptions(color, DUST_SIZE);

        for (int x = minX; x <= maxX; x += STEP) {
            spawnAtGroundPlus2(world, x, minZ, dust);
            spawnAtGroundPlus2(world, x, maxZ, dust);
        }
        for (int z = minZ; z <= maxZ; z += STEP) {
            spawnAtGroundPlus2(world, minX, z, dust);
            spawnAtGroundPlus2(world, maxX, z, dust);
        }
    }

    /** Rendu d’une frame pour TOUS les claims (colorés par royaume) dans ce monde. */
    public static void renderAllClaimsOnceForWorld(World world) {
        for (Claim c : ClaimManager.getClaims()) {
            String kname = c.getKingdomName();
            if (kname == null || "None".equalsIgnoreCase(kname)) continue;

            Kingdom k = KingdomsManager.getKingdomByName(kname);
            Color color = (k != null) ? chatToColor(k.getColor()) : Color.fromRGB(128,128,128);

            drawClaimOutlineForWorld(world, c.getGridX(), c.getGridZ(), color);
        }
    }

    /** Particule à 2 blocs au-dessus du sol local (hauteur dynamique). */
    private static void spawnAtGroundPlus2(World w, int x, int z, Particle.DustOptions dust) {
        int groundY = w.getHighestBlockYAt(x, z);
        Location loc = new Location(w, x + 0.5, groundY + 2.0, z + 0.5);
        w.spawnParticle(Particle.DUST, loc, COUNT_PER_SPAWN, 0, 0, 0, 0, dust, true);
    }

    /** Mapping ChatColor -> org.bukkit.Color */
    public static Color chatToColor(org.bukkit.ChatColor cc) {
        if (cc == null) return Color.fromRGB(255,255,255);
        switch (cc) {
            case DARK_RED:   return Color.fromRGB(170, 0, 0);
            case RED:        return Color.fromRGB(255, 85, 85);
            case GOLD:       return Color.fromRGB(255, 170, 0);
            case YELLOW:     return Color.fromRGB(255, 255, 85);
            case DARK_GREEN: return Color.fromRGB(0, 170, 0);
            case GREEN:      return Color.fromRGB(85, 255, 85);
            case AQUA:       return Color.fromRGB(85, 255, 255);
            case DARK_AQUA:  return Color.fromRGB(0, 170, 170);
            case DARK_BLUE:  return Color.fromRGB(0, 0, 170);
            case BLUE:       return Color.fromRGB(85, 85, 255);
            case LIGHT_PURPLE:return Color.fromRGB(255, 85, 255);
            case DARK_PURPLE:return Color.fromRGB(170, 0, 170);
            case WHITE:      return Color.fromRGB(255, 255, 255);
            case GRAY:       return Color.fromRGB(170, 170, 170);
            case DARK_GRAY:  return Color.fromRGB(85, 85, 85);
            case BLACK:      return Color.fromRGB(0, 0, 0);
            default:         return Color.fromRGB(255, 255, 255);
        }
    }

    private static org.bukkit.plugin.Plugin getPlugin() {
        // Récupère le plugin via le serveur (évite import de FactionsPlugin ici)
        return Bukkit.getPluginManager().getPlugins()[0]; // ou mieux: injecter via un setter statique à l'init
    }
}
