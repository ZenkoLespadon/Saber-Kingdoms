package com.kingdomspvp.kingdoms.utils;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ClaimVisualization {

    private ClaimVisualization() {}

    // -------------------------------------------------
    // Plugin
    // -------------------------------------------------

    private static Plugin PLUGIN;

    public static void init(Plugin plugin) {
        PLUGIN = plugin;
        reloadSettings();
    }

    private static Plugin getPlugin() {
        return PLUGIN;
    }

    // -------------------------------------------------
    // Runtime settings (RELOADABLES)
    // -------------------------------------------------

    private static float DUST_SIZE;
    private static int STEP;
    private static long PERIOD_TICKS;
    private static int COUNT_PER_SPAWN;

    private static int MAX_SPAWNS_PER_TICK;
    private static int MAX_DIST_SQ;

    // -------------------------------------------------
    // Runtime state
    // -------------------------------------------------

    private static int spawnsThisTick = 0;

    // taskId par guerre
    private static final Map<String, Integer> WAR_TASKS = new ConcurrentHashMap<>();

    // -------------------------------------------------
    // Reload
    // -------------------------------------------------

    public static void reloadSettings() {
        var p = SettingsProvider.get().visualization().particles();

        DUST_SIZE = p.dustSize();
        STEP = p.step();
        PERIOD_TICKS = p.periodTicks();
        COUNT_PER_SPAWN = p.countPerSpawn();

        MAX_SPAWNS_PER_TICK = p.maxSpawnsPerTick();

        int maxDistBlocks = p.maxDistanceBlocks();
        MAX_DIST_SQ = maxDistBlocks * maxDistBlocks;
    }

    // -------------------------------------------------
    // War outlines
    // -------------------------------------------------

    public static void startWarOutlines(War war) {
        stopWarOutlines(war);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                getPlugin(),
                () -> {
                    beginFrameBudget();

                    Set<World> worlds = Bukkit.getOnlinePlayers()
                            .stream()
                            .map(Player::getWorld)
                            .collect(Collectors.toSet());

                    if (worlds.isEmpty()) return;

                    for (World w : worlds) {
                        renderAttackableClaimsOnceForWorld(w, war);
                    }
                },
                0L,
                PERIOD_TICKS
        );

        WAR_TASKS.put(war.getId(), taskId);
    }

    public static void switchToAttackedClaimOnly(War war, Claim attackedClaim) {
        stopWarOutlines(war);

        int gx = attackedClaim.getGridX();
        int gz = attackedClaim.getGridZ();

        ChatColor chat = ChatColor.GRAY;
        Kingdom k = KingdomsManager.getKingdomByName(attackedClaim.getKingdomName());
        if (k != null) chat = k.getColor();

        Color color = chatToColor(chat);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                getPlugin(),
                () -> {
                    beginFrameBudget();

                    Set<World> worlds = Bukkit.getOnlinePlayers()
                            .stream()
                            .map(Player::getWorld)
                            .collect(Collectors.toSet());

                    if (worlds.isEmpty()) return;

                    for (World w : worlds) {
                        drawClaimOutlineForWorld(w, gx, gz, color);
                    }
                },
                0L,
                PERIOD_TICKS
        );

        WAR_TASKS.put(war.getId(), taskId);
    }

    public static void stopWarOutlines(War war) {
        Integer id = WAR_TASKS.remove(war.getId());
        if (id != null) Bukkit.getScheduler().cancelTask(id);
    }

    // -------------------------------------------------
    // Rendering
    // -------------------------------------------------

    private static void renderAttackableClaimsOnceForWorld(World world, War war) {
        Color color = chatToColor(war.getDefenderKingdom().getColor());
        String def = war.getDefenderKingdom().getName();

        for (Claim c : ClaimManager.getDefenderClaimsAdjacentToAttacker(
                war.getDefenderKingdom().getName(),
                war.getAttackerKingdom().getName()
        )) {
            if (!def.equalsIgnoreCase(c.getKingdomName())) continue;
            drawClaimOutlineForWorld(world, c.getGridX(), c.getGridZ(), color);
        }
    }

    public static void renderAllClaimsOnceForWorld(World world) {
        for (Claim c : ClaimManager.getClaims()) {
            String kname = c.getKingdomName();
            if (kname == null || "None".equalsIgnoreCase(kname)) continue;

            Kingdom k = KingdomsManager.getKingdomByName(kname);
            Color color = (k != null)
                    ? chatToColor(k.getColor())
                    : Color.fromRGB(128, 128, 128);

            drawClaimOutlineForWorld(world, c.getGridX(), c.getGridZ(), color);

            if (overBudget()) return;
        }
    }

    public static void drawClaimOutlineForWorld(World world, int gridX, int gridZ, Color color) {
        int size = ClaimManager.getActiveClaimSize();

        int minX = gridX * size;
        int minZ = gridZ * size;
        int maxX = minX + size;
        int maxZ = minZ + size;

        if (!claimNearAnyPlayer(world, minX, minZ, maxX, maxZ)) return;

        Particle.DustOptions dust = new Particle.DustOptions(color, DUST_SIZE);

        for (int x = minX; x <= maxX; x += STEP) {
            spawn(world, x, minZ, dust);
            spawn(world, x, maxZ, dust);
            if (overBudget()) return;
        }

        for (int z = minZ; z <= maxZ; z += STEP) {
            spawn(world, minX, z, dust);
            spawn(world, maxX, z, dust);
            if (overBudget()) return;
        }
    }

    // -------------------------------------------------
    // Budget
    // -------------------------------------------------

    public static void beginFrameBudget() {
        spawnsThisTick = 0;
    }

    private static boolean overBudget() {
        return spawnsThisTick >= MAX_SPAWNS_PER_TICK;
    }

    private static void spawn(World w, int x, int z, Particle.DustOptions dust) {
        if (overBudget()) return;

        int y = w.getHighestBlockYAt(x, z);
        Location loc = new Location(w, x + 0.5, y + 2.0, z + 0.5);

        w.spawnParticle(
                Particle.DUST,
                loc,
                COUNT_PER_SPAWN,
                0, 0, 0, 0,
                dust,
                true
        );

        spawnsThisTick++;
    }

    // -------------------------------------------------
    // Proximity check
    // -------------------------------------------------

    private static boolean claimNearAnyPlayer(World w, int minX, int minZ, int maxX, int maxZ) {
        if (w.getPlayers().isEmpty()) return false;

        int midX = (minX + maxX) >> 1;
        int midZ = (minZ + maxZ) >> 1;

        int[][] pts = {
                {minX, minZ}, {minX, maxZ},
                {maxX, minZ}, {maxX, maxZ},
                {midX, midZ}
        };

        for (Player p : w.getPlayers()) {
            int px = p.getLocation().getBlockX();
            int pz = p.getLocation().getBlockZ();

            for (int[] pt : pts) {
                int dx = pt[0] - px;
                int dz = pt[1] - pz;
                if (dx * dx + dz * dz <= MAX_DIST_SQ) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------
    // Colors
    // -------------------------------------------------

    public static Color chatToColor(ChatColor cc) {
        if (cc == null) return Color.WHITE;

        return switch (cc) {
            case DARK_RED    -> Color.fromRGB(170, 0, 0);
            case RED         -> Color.fromRGB(255, 85, 85);
            case GOLD        -> Color.fromRGB(255, 170, 0);
            case YELLOW      -> Color.fromRGB(255, 255, 85);
            case DARK_GREEN  -> Color.fromRGB(0, 170, 0);
            case GREEN       -> Color.fromRGB(85, 255, 85);
            case AQUA        -> Color.fromRGB(85, 255, 255);
            case DARK_AQUA   -> Color.fromRGB(0, 170, 170);
            case DARK_BLUE   -> Color.fromRGB(0, 0, 170);
            case BLUE        -> Color.fromRGB(85, 85, 255);
            case LIGHT_PURPLE-> Color.fromRGB(255, 85, 255);
            case DARK_PURPLE -> Color.fromRGB(170, 0, 170);
            case GRAY        -> Color.fromRGB(170, 170, 170);
            case DARK_GRAY   -> Color.fromRGB(85, 85, 85);
            case BLACK       -> Color.BLACK;
            default          -> Color.WHITE;
        };
    }
}
