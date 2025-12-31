package com.kingdomspvp.kingdoms.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public final class KingdomsConfig {

    private static FileConfiguration cfg;
    private static boolean VALID = false;

    // ===== CLAIMS (INTENTION ADMIN) =====
    public static int CONFIG_MAP_SIZE;
    public static int CONFIG_CLAIM_SIZE;

    // ===== VISUALIZATION =====
    public static float DUST_SIZE;
    public static int STEP;
    public static int COUNT_PER_SPAWN;
    public static int PERIOD_TICKS;
    public static int MAX_SPAWNS_PER_TICK;
    public static int MAX_DISTANCE_BLOCKS;

    // ===== COMMANDS =====
    public static int VIZCLAIMS_DEFAULT_SECONDS;
    public static int VIZCLAIMS_MIN_SECONDS;
    public static int VIZCLAIMS_MAX_SECONDS;

    // ===== WAR =====
    public static boolean WAR_TEST_MODE;
    public static int WAR_MIN_TIME_MIN;
    public static int WAR_MAX_TIME_HOURS;
    public static int WAR_JOIN_PROMPT_LEAD_SEC;
    public static int WAR_DETECTION_WINDOW_SEC;

    public static int WAR_DURATION_SEC;
    public static int WAR_MAX_ROUNDS;
    public static double WAR_ROUND_GAIN_FACTOR;
    public static int WAR_TARGET_POINTS;

    // ===== TELEPORT =====
    public static int TP_OFFSET_FROM_BORDER;
    public static int TP_Y_OFFSET;

    // ===== KDA =====
    public static int KDA_ASSIST_WINDOW_SEC;
    public static boolean KDA_COUNT_SELF_DAMAGE;

    // ===== BLOCKS =====
    public static int BLOCK_REVERT_AFTER_SEC;
    public static List<String> BLOCKS_FORBIDDEN;

    // ===== DEATH =====
    public static int TP_AFTER_DEATH_DELAY_SEC;

    private KingdomsConfig() {}

    public static boolean load(Plugin plugin) {
        VALID = false;

        File file = new File(plugin.getDataFolder(), "config_kingdoms.yml");
        if (!file.exists()) {
            plugin.getLogger().severe("[KingdomsConfig] config_kingdoms.yml manquant.");
            return false;
        }

        cfg = YamlConfiguration.loadConfiguration(file);

        try {
            CONFIG_MAP_SIZE  = requireInt("claims.map-size");
            CONFIG_CLAIM_SIZE = requireInt("claims.claim-size");

            DUST_SIZE = (float) requireDouble("visualization.particles.dust-size");
            STEP = requireInt("visualization.particles.step");
            COUNT_PER_SPAWN = requireInt("visualization.particles.count-per-spawn");
            PERIOD_TICKS = requireInt("visualization.particles.period-ticks");
            MAX_SPAWNS_PER_TICK = requireInt("visualization.particles.max-spawns-per-tick");
            MAX_DISTANCE_BLOCKS = requireInt("visualization.particles.max-distance-blocks");

            VIZCLAIMS_DEFAULT_SECONDS = requireInt("commands.vizclaims.default-seconds");
            VIZCLAIMS_MIN_SECONDS = requireInt("commands.vizclaims.min-seconds");
            VIZCLAIMS_MAX_SECONDS = requireInt("commands.vizclaims.max-seconds");

            WAR_TEST_MODE = requireBoolean("war.test-mode");

            WAR_MIN_TIME_MIN = requireInt("war.planning.min-time-before-war-minutes");
            WAR_MAX_TIME_HOURS = requireInt("war.planning.max-time-before-war-hours");
            WAR_JOIN_PROMPT_LEAD_SEC = requireInt("war.planning.join-prompt-lead-seconds");
            WAR_DETECTION_WINDOW_SEC = requireInt("war.detection.window-seconds");

            WAR_DURATION_SEC = requireInt("war.combat.duration-seconds");
            WAR_MAX_ROUNDS = requireInt("war.combat.max-rounds");
            WAR_ROUND_GAIN_FACTOR = requireDouble("war.combat.round-gain-factor");
            WAR_TARGET_POINTS = requireInt("war.combat.target-points");

            TP_OFFSET_FROM_BORDER = requireInt("war.teleport.offset-from-border");
            TP_Y_OFFSET = requireInt("war.teleport.y-offset");

            KDA_ASSIST_WINDOW_SEC = requireInt("war.kda.assist-window-seconds");
            KDA_COUNT_SELF_DAMAGE = requireBoolean("war.kda.count-self-damage");

            BLOCK_REVERT_AFTER_SEC = requireInt("blocks.revert-after-seconds");
            BLOCKS_FORBIDDEN = cfg.getStringList("blocks.forbidden");

            TP_AFTER_DEATH_DELAY_SEC = requireInt("death.tp-message-delay-seconds");

        } catch (IllegalStateException e) {
            plugin.getLogger().severe("[KingdomsConfig] ERREUR CONFIG : " + e.getMessage());
            return false;
        }

        VALID = true;
        return true;
    }

    public static void warnClaimSizeMismatch(int metaClaimSize) {
        if (metaClaimSize == CONFIG_CLAIM_SIZE) return;

        Bukkit.getLogger().warning("[SaberKingdoms] ⚠ claim-size différent !");
        Bukkit.getLogger().warning("[SaberKingdoms] - Valeur active (meta) : " + metaClaimSize);
        Bukkit.getLogger().warning("[SaberKingdoms] - Valeur config        : " + CONFIG_CLAIM_SIZE);
        Bukkit.getLogger().warning("[SaberKingdoms] ➜ Le serveur utilise la valeur META.");
        Bukkit.getLogger().warning("[SaberKingdoms] ➜ /k reload hard pour régénérer les claims.");
    }


    public static void warnMapSizeMismatch(int metaMapSize) {
        if (metaMapSize == CONFIG_MAP_SIZE) return;

        Bukkit.getLogger().warning("[SaberKingdoms] ⚠ map-size différent !");
        Bukkit.getLogger().warning("[SaberKingdoms] - Valeur active (meta) : " + metaMapSize);
        Bukkit.getLogger().warning("[SaberKingdoms] - Valeur config        : " + CONFIG_MAP_SIZE);
        Bukkit.getLogger().warning("[SaberKingdoms] ➜ Le serveur utilise la valeur META.");
        Bukkit.getLogger().warning("[SaberKingdoms] ➜ /k reload hard pour régénérer les claims.");
    }




    private static int requireInt(String path) {
        if (!cfg.isInt(path)) throw new IllegalStateException(path);
        return cfg.getInt(path);
    }
    private static double requireDouble(String path) {
        if (!cfg.isDouble(path) && !cfg.isInt(path)) throw new IllegalStateException(path);
        return cfg.getDouble(path);
    }
    private static boolean requireBoolean(String path) {
        if (!cfg.isBoolean(path)) throw new IllegalStateException(path);
        return cfg.getBoolean(path);
    }

    public static boolean isValid() {
        return VALID;
    }

    public static void ensureDefaultConfig(Plugin plugin) {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, "config_kingdoms.yml");
        if (file.exists()) return;

        try {
            String content = """
# =========================
# Kingdoms - Configuration
# =========================

# -------------------------
# CLAIMS
# Taille globale de la map et des claims.
# ⚠️ Modifier claim-size nécessite /k reload hard
# -------------------------
claims:
  map-size: 1536
  claim-size: 64


# -------------------------
# VISUALISATION DES CLAIMS
# Particules utilisées pour afficher les claims.
# Attention : des valeurs trop élevées peuvent provoquer du lag.
# -------------------------
visualization:
  particles:
    dust-size: 5.0
    step: 4
    count-per-spawn: 1
    period-ticks: 10
    max-spawns-per-tick: 5000
    max-distance-blocks: 256


# -------------------------
# COMMANDES
# Paramètres des commandes administrateur.
# -------------------------
commands:
  vizclaims:
    default-seconds: 30
    min-seconds: 1
    max-seconds: 300
    period-ticks: 10


# -------------------------
# GUERRES
# Paramètres globaux du système de guerre.
# -------------------------
war:
  # Active le mode test (délais raccourcis, règles assouplies)
  test-mode: true

  # Phase de planification avant la guerre
  planning:
    min-time-before-war-minutes: 1
    max-time-before-war-hours: 48
    join-prompt-lead-seconds: 30

  # Fenêtre de détection du premier assaut
  detection:
    window-seconds: 120

  # Déroulement du combat
  combat:
    duration-seconds: 150
    max-rounds: 4
    round-gain-factor: 0.87
    target-points: 1000
    target-fraction: 0.80
    ratio:
      min: 0.5
      max: 2.0

  # Téléportation pendant la guerre
  teleport:
    offset-from-border: 10
    y-offset: 1

  # Statistiques KDA
  kda:
    assist-window-seconds: 10
    count-self-damage: true


# -------------------------
# BLOCS EN ZONE DE GUERRE
# Restauration automatique des blocs modifiés.
# -------------------------
blocks:
  revert-after-seconds: 20
  forbidden:
    - TNT
    - FIRE


# -------------------------
# MORT EN GUERRE
# Délai avant proposition de téléportation.
# -------------------------
death:
  tp-message-delay-seconds: 15
""";

            Files.writeString(file.toPath(), content);
            plugin.getLogger().info("[SaberKingdoms] config_kingdoms.yml généré par défaut.");

        } catch (Exception e) {
            Bukkit.getLogger().severe("[SaberKingdoms] Impossible de créer config_kingdoms.yml");
            e.printStackTrace();
        }
    }

}
