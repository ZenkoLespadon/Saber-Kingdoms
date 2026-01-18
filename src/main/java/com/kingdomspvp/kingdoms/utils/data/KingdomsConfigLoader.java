package com.kingdomspvp.kingdoms.utils.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;

public final class KingdomsConfigLoader {

    private KingdomsConfigLoader() {}

    public static KingdomsSettings load(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "config_kingdoms.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        int mapSize = cfg.getInt("claims.map-size");
        int claimSize = cfg.getInt("claims.claim-size");

        var particles = new KingdomsSettings.Visualization.Particles(
                (float) cfg.getDouble("visualization.particles.dust-size"),
                cfg.getInt("visualization.particles.step"),
                cfg.getInt("visualization.particles.count-per-spawn"),
                cfg.getInt("visualization.particles.period-ticks"),
                cfg.getInt("visualization.particles.max-spawns-per-tick"),
                cfg.getInt("visualization.particles.max-distance-blocks")
        );

        var viz = new KingdomsSettings.Visualization(particles);

        var vizClaims = new KingdomsSettings.Commands.VizClaims(
                cfg.getInt("commands.vizclaims.default-seconds"),
                cfg.getInt("commands.vizclaims.min-seconds"),
                cfg.getInt("commands.vizclaims.max-seconds"),
                cfg.getInt("commands.vizclaims.period-ticks")
        );

        var commands = new KingdomsSettings.Commands(vizClaims);

        var planning = new KingdomsSettings.War.Planning(
                cfg.getInt("war.planning.min-time-before-war-minutes"),
                cfg.getInt("war.planning.max-time-before-war-hours"),
                cfg.getInt("war.planning.join-prompt-lead-seconds")
        );

        var detection = new KingdomsSettings.War.Detection(
                cfg.getInt("war.detection.window-seconds")
        );

        var combat = new KingdomsSettings.War.Combat(
                cfg.getInt("war.combat.duration-seconds"),
                cfg.getInt("war.combat.max-rounds"),
                cfg.getDouble("war.combat.round-gain-factor"),
                cfg.getInt("war.combat.target-points"),
                cfg.getDouble("war.combat.target-fraction"),
                cfg.getDouble("war.combat.ratio.min"),
                cfg.getDouble("war.combat.ratio.max")
        );

        var teleport = new KingdomsSettings.War.Teleport(
                cfg.getInt("war.teleport.offset-from-border"),
                cfg.getInt("war.teleport.y-offset")
        );

        var kda = new KingdomsSettings.War.Kda(
                cfg.getInt("war.kda.assist-window-seconds"),
                cfg.getBoolean("war.kda.count-self-damage")
        );

        var war = new KingdomsSettings.War(
                cfg.getBoolean("war.test-mode"),
                planning, detection, combat, teleport, kda
        );

        var blocks = new KingdomsSettings.Blocks(
                cfg.getInt("blocks.revert-after-seconds"),
                cfg.getStringList("blocks.forbidden")
        );

        var death = new KingdomsSettings.Death(
                cfg.getInt("death.tp-message-delay-seconds")
        );

        String serverType = cfg.getString("server.type", "KINGDOMS").toUpperCase();

        if (!List.of("HUB","KINGDOMS","MINAGE").contains(serverType)) {
            throw new IllegalStateException("server.type");
        }


        return new KingdomsSettings(
                serverType,
                new KingdomsSettings.Claims(mapSize, claimSize),
                viz, commands, war, blocks, death
        );
    }
}
