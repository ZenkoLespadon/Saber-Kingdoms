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
        String content = """
                  # =========================
                  # Kingdoms - Configuration
                  # =========================
                  # Ce fichier contrôle l’ensemble du système de royaumes, claims et guerres.
                  # Toute modification est prise en compte après /k reload ou un redémarrage du serveur.
                  # Certaines valeurs nécessitent /k reload hard (voir sections concernées).
             
             
             
                  # -------------------------
                  # CLAIMS
                  # Définit la taille de la map et des claims.
                  #
                  # IMPORTANT :
                  # - Modifier ces valeurs nécessite /k reload hard
                  # - Cette action SUPPRIME et RÉGÉNÈRE tous les claims existants
                  # -------------------------
                  claims:
                    # Taille totale de la map carrée (en blocs).
                    # Exemple : 1536 = map de -768 à +768 sur X et Z.
                    map-size: 1536
        
                    # Taille d’un claim (en blocs).
                    # Exemple : 64 = chaque claim fait 64x64 blocs.
                    claim-size: 64
        
        
                  # -------------------------
                  # VISUALISATION DES CLAIMS
                  # Paramètres utilisés pour afficher les claims avec des particules.
                  # Affecte les performances serveur.
                  # -------------------------
                  visualization:
                    particles:
                      # Taille des particules.
                      dust-size: 5.0
        
                      # Espace entre chaque particule autour d’un claim.
                      step: 4
        
                      # Nombre de particules générées par point (Laisser à 1 sauf besoin spécifique).
                      count-per-spawn: 1
        
                      # Fréquence de rafraîchissement des particules en ticks (20 ticks = 1 seconde).
                      period-ticks: 10
        
                      # Nombre maximal de particules générées par tick (protège contre les surcharges).
                      max-spawns-per-tick: 5000
        
                      # Distance maximale à laquelle un joueur peut voir les particules.
                      max-distance-blocks: 256
        
        
                  # -------------------------
                  # COMMANDES
                  # Paramètres des commandes administrateur.
                  # -------------------------
                  commands:
                    vizclaims:
                      # Durée par défaut (en secondes) de la commande /k vizclaims
                      default-seconds: 30
        
                      # Durée minimale autorisée
                      min-seconds: 1
        
                      # Durée maximale autorisée
                      max-seconds: 300
        
                      # Fréquence de rafraîchissement des particules pour cette commande
                      period-ticks: 10
        
        
                  # -------------------------
                  # GUERRES
                  # Paramètres globaux du système de guerres entre royaumes.
                  # -------------------------
                  war:
                    # Utile pour le développement ou les tests (délais plus courts)
                    test-mode: true
        
                    # ---------------------
                    # PLANIFICATION
                    # Phase entre la déclaration et le début potentiel de la guerre.
                    # ---------------------
                    planning:
                      # Temps minimum avant le début de la guerre
                      min-time-before-war-minutes: 1
        
                      # Temps maximum avant le début de la guerre
                      max-time-before-war-hours: 48
        
                      # Délai avant l’envoi du message demandant aux joueurs de rejoindre la guerre.
                      join-prompt-lead-seconds: 30
        
                    # ---------------------
                    # DÉTECTION
                    # Fenêtre pendant laquelle les attaquants doivent entrer dans un claim pour déclencher la manche.
                    # ---------------------
                    detection:
                      window-seconds: 120
        
                    # ---------------------
                    # COMBAT
                    # Paramètres du déroulement des rounds de guerre.
                    # Gagner une manche permet de capturer un claim.
                    # Pour gagner une manche, les attaquants doivent atteindre target-points avant la fin de la manche
                    # Et les défenseurs doivent les en empêcher pendant 1 manche pour arrêter la guerre.
                    # Au fil des manches, il devient de plus en plus difficile pour les attaquants de gagner une manche.
                    # ---------------------
                    combat:
                      # Durée maximale d’une manche
                      duration-seconds: 150
        
                      # Nombre maximum de rounds dans une guerre
                      max-rounds: 4
        
                      # Facteur de progression par round.
                      # Plus bas = manche plus dure pour les attaquants.
                      round-gain-factor: 0.87
        
                      # Nombre de points nécessaires pour capturer un claim
                      target-points: 1000
        
                      # Pourcentage de temps où un ratio 1:1 attaquants/défenseurs donne target-points en target-fraction du temps.
                      # Exemple : target-fraction = 0.80, target-points = 1000, duraction-seconds = 150
                      # Les attaquants (avec un ratio 1:1) atteignent 1000 points en 120 secondes (80% de 150s).
                      target-fraction: 0.80
        
                      # Bornes de sécurité pour les ratios attaquants/défenseurs.
                      # Évite les gains énormes en cas de déséquilibre.
                      ratio:
                        min: 0.5
                        max: 2.0
        
                    # ---------------------
                    # TÉLÉPORTATION
                    # Paramètres des téléportations pendant les guerres.
                    # ---------------------
                    teleport:
                      # Distance de la bordure du claim lors d'une téléportation.
                      offset-from-border: 10
        
                      # Distance du sol lors d'une téléportation.
                      y-offset: 1
        
                    # ---------------------
                    # KDA
                    # Gestion des statistiques de kills, deaths et assists.
                    # ---------------------
                    kda:
                      # Fenêtre pendant laquelle un joueur est compté comme assistant après avoir infligé des dégâts.
                      assist-window-seconds: 10
        
                      # Comptabiliser les dégâts auto-infligés dans le KDA.
                      # Si true :
                      # - Les joueurs qui meurent sans se faire tapper sont comptés dans le KDA
                      # Si false :
                      # - Ils sont ignorés du le calcul du KDA.
                      count-self-damage: true
        
        
                  # -------------------------
                  # BLOCS PENDANT LES GUERRES
                  # -------------------------
                  blocks:
                    # Temps avant restauration automatique des blocs modifiés
                    revert-after-seconds: 20
        
                    # Liste des blocs interdits à placer pendant une guerre
                    forbidden:
                      - TNT
                      - FIRE
        
        
                  # -------------------------
                  # MORT EN GUERRE
                  # Comportement après la mort d’un joueur pendant un combat.
                  # -------------------------
                  death:
                    # Délai avant l’envoi du message proposant une téléportation vers la guerre en cours.
                    tp-message-delay-seconds: 15
                  """;
        if (file.exists()) return;

        try {

            Files.writeString(file.toPath(), content);
            plugin.getLogger().info("[SaberKingdoms] config_kingdoms.yml généré par défaut.");

        } catch (Exception e) {
            Bukkit.getLogger().severe("[SaberKingdoms] Impossible de créer config_kingdoms.yml");
            e.printStackTrace();
        }
    }

}
