package org.fr.kingdomspvp;

import org.bukkit.plugin.java.JavaPlugin;

public final class SaberKingdoms extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("SaberKingdoms has been enabled!");

        // Register commands, events, and other initializations here
        // Example: getCommand("yourcommand").setExecutor(new YourCommandExecutor());

        // Load configuration files if needed
        saveDefaultConfig();

        // Initialize any services or managers
        // Example: KingdomManager.initialize();

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
