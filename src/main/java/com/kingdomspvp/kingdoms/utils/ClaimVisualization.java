// src/main/java/com/kingdomspvp/kingdoms/utils/ClaimVisualization.java
package com.kingdomspvp.kingdoms.utils;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ClaimVisualization {

    private ClaimVisualization() {}

    // --- Injection plugin (évite getPlugins()[0]) ---
    private static Plugin PLUGIN;
    public static void init(Plugin plugin) { PLUGIN = plugin; }
    private static Plugin getPlugin() { return PLUGIN; }

    // --- Réglages visuels/perf ---
    private static final float  DUST_SIZE          = 5.0f;
    private static final int    STEP               = 4;
    private static final long   PERIOD_TICKS       = 10L;
    private static final int    COUNT_PER_SPAWN    = 1;

    // Budget anti-lag
    private static final int MAX_SPAWNS_PER_TICK = 5000;
    private static int spawnsThisTick = 0;

    // Distance max par rapport à un joueur (carrée) pour dessiner
    private static final int MAX_DIST_BLOCKS = 256;
    private static final int MAX_DIST_SQ = MAX_DIST_BLOCKS * MAX_DIST_BLOCKS;

    // Un taskId par guerre
    private static final Map<String, Integer> WAR_TASKS = new ConcurrentHashMap<>();

    // --- API publique (appelée par WarManager) ---

    private static List<Claim> getAttackableClaims(War war) {
        String def = war.getDefenderKingdom().getName();
        String atk = war.getAttackerKingdom().getName();
        return ClaimManager.getDefenderClaimsAdjacentToAttacker(def, atk);
    }

    private static void renderAttackableClaimsOnceForWorld(World world, War war) {
        Color color = chatToColor(war.getDefenderKingdom().getColor());
        String def = war.getDefenderKingdom().getName();

        for (Claim c : getAttackableClaims(war)) {
            if (!def.equalsIgnoreCase(c.getKingdomName())) continue;
            drawClaimOutlineForWorld(world, c.getGridX(), c.getGridZ(), color);
        }
    }

    public static void startWarOutlines(War war) {
        stopWarOutlines(war);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                getPlugin(),
                () -> {
                    beginFrameBudget();
                    Set<World> worlds = Bukkit.getOnlinePlayers()
                            .stream().map(Player::getWorld)
                            .collect(Collectors.toSet());
                    if (worlds.isEmpty()) return;
                    for (World w : worlds) {
                        renderAttackableClaimsOnceForWorld(w, war);
                    }
                },
                0L, PERIOD_TICKS
        );
        WAR_TASKS.put(war.getId(), taskId);
    }

    public static void switchToAttackedClaimOnly(War war, Claim attackedClaim) {
        stopWarOutlines(war);
        final int gx = attackedClaim.getGridX();
        final int gz = attackedClaim.getGridZ();

        ChatColor chat = ChatColor.GRAY;
        Kingdom k = KingdomsManager.getKingdomByName(attackedClaim.getKingdomName());
        if (k != null) chat = k.getColor();
        final Color color = chatToColor(chat);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                getPlugin(),
                () -> {
                    beginFrameBudget();
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

    public static void stopWarOutlines(War war) {
        Integer id = WAR_TASKS.remove(war.getId());
        if (id != null) Bukkit.getScheduler().cancelTask(id);
    }

    // --- Rendu de base ---

    public static void drawClaimOutlineForWorld(World world, int gridX, int gridZ, Color color) {
        int size = ClaimManager.getActiveClaimSize(); // ex: 128
        int minX = gridX * size;
        int minZ = gridZ * size;
        int maxX = minX + size;
        int maxZ = minZ + size;

        // Filtrage grossier par proximité joueur (évite de travailler loin)
        if (!claimNearAnyPlayer(world, minX, minZ, maxX, maxZ)) return;

        Particle.DustOptions dust = new Particle.DustOptions(color, DUST_SIZE);

        for (int x = minX; x <= maxX; x += STEP) {
            spawnAtGroundPlus2(world, x, minZ, dust);
            spawnAtGroundPlus2(world, x, maxZ, dust);
            if (overBudget()) return;
        }
        for (int z = minZ; z <= maxZ; z += STEP) {
            spawnAtGroundPlus2(world, minX, z, dust);
            spawnAtGroundPlus2(world, maxX, z, dust);
            if (overBudget()) return;
        }
    }

    public static void renderAllClaimsOnceForWorld(World world) {
        for (Claim c : ClaimManager.getClaims()) {
            String kname = c.getKingdomName();
            if (kname == null || "None".equalsIgnoreCase(kname)) continue;

            Kingdom k = KingdomsManager.getKingdomByName(kname);
            Color color = (k != null) ? chatToColor(k.getColor()) : Color.fromRGB(128,128,128);

            drawClaimOutlineForWorld(world, c.getGridX(), c.getGridZ(), color);
            if (overBudget()) return;
        }
    }

    // --- Particules / budget ---

    public static void beginFrameBudget() { spawnsThisTick = 0; }
    private static boolean overBudget() { return spawnsThisTick >= MAX_SPAWNS_PER_TICK; }

    private static void spawnAtGroundPlus2(World w, int x, int z, Particle.DustOptions dust) {
        if (overBudget()) return;
        int groundY = w.getHighestBlockYAt(x, z);
        Location loc = new Location(w, x + 0.5, groundY + 2.0, z + 0.5);
        w.spawnParticle(Particle.DUST, loc, COUNT_PER_SPAWN, 0, 0, 0, 0, dust, true);
        spawnsThisTick++;
    }

    // --- Proximité joueurs ---

    private static boolean claimNearAnyPlayer(World w, int minX, int minZ, int maxX, int maxZ) {
        if (w.getPlayers().isEmpty()) return false;

        int midX = (minX + maxX) >> 1;
        int midZ = (minZ + maxZ) >> 1;

        // Points sentinelles (4 coins + centre) pour un test rapide
        int[][] points = {
                {minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}, {midX, midZ}
        };

        for (Player p : w.getPlayers()) {
            Location pl = p.getLocation();
            int px = pl.getBlockX();
            int pz = pl.getBlockZ();
            for (int[] pt : points) {
                int dx = pt[0] - px;
                int dz = pt[1] - pz;
                if ((dx*dx + dz*dz) <= MAX_DIST_SQ) return true;
            }
        }
        return false;
    }

    // --- Utilitaires ---

    public static Color chatToColor(ChatColor cc) {
        if (cc == null) return Color.fromRGB(255,255,255);
        switch (cc) {
            case DARK_RED:    return Color.fromRGB(170, 0, 0);
            case RED:         return Color.fromRGB(255, 85, 85);
            case GOLD:        return Color.fromRGB(255, 170, 0);
            case YELLOW:      return Color.fromRGB(255, 255, 85);
            case DARK_GREEN:  return Color.fromRGB(0, 170, 0);
            case GREEN:       return Color.fromRGB(85, 255, 85);
            case AQUA:        return Color.fromRGB(85, 255, 255);
            case DARK_AQUA:   return Color.fromRGB(0, 170, 170);
            case DARK_BLUE:   return Color.fromRGB(0, 0, 170);
            case BLUE:        return Color.fromRGB(85, 85, 255);
            case LIGHT_PURPLE:return Color.fromRGB(255, 85, 255);
            case DARK_PURPLE: return Color.fromRGB(170, 0, 170);
            case WHITE:       return Color.fromRGB(255, 255, 255);
            case GRAY:        return Color.fromRGB(170, 170, 170);
            case DARK_GRAY:   return Color.fromRGB(85, 85, 85);
            case BLACK:       return Color.fromRGB(0, 0, 0);
            default:          return Color.fromRGB(255, 255, 255);
        }
    }
}
