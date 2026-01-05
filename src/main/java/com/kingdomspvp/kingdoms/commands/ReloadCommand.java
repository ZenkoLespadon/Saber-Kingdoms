// src/main/java/com/kingdomspvp/kingdoms/commands/ReloadCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.utils.KingdomsConfig;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.KingdomsConfigLoader;
import com.kingdomspvp.kingdoms.utils.KingdomsSettings;
import com.kingdomspvp.kingdoms.utils.SettingsProvider;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.Collections;

public class ReloadCommand extends KingdomCommand {

    private final FactionsPlugin plugin;

    public ReloadCommand(FactionsPlugin plugin) {
        this.plugin = plugin;

        this.aliases = Arrays.asList("reload");
        this.requiredArgs = Collections.emptyList();
        this.optionalArgs.put("hard", "");
        this.helpShort = ChatColor.GRAY + "Recharge la config (hard = régénère les claims)";

        this.permission = "kingdoms.admin"; // adapte si besoin
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (!hasPermission(context.sender)) {
            context.msg(ChatColor.RED + "Vous n'avez pas la permission.");
            return;
        }

        boolean hard = !context.args.isEmpty()
                && context.args.get(0).equalsIgnoreCase("hard");

        if (hard) {
            reloadHard(context);
        } else {
            reloadSoft(context);
        }
    }


    private void reloadSoft(KingdomCommandContext context) {
        KingdomsConfig.ensureDefaultConfig(plugin);

        KingdomsSettings settings = KingdomsConfigLoader.load(plugin);
        SettingsProvider.set(settings);

        // check meta vs config (intention)
        Integer metaClaim = ClaimManager.getStoredClaimSize();
        Integer metaMap   = ClaimManager.getStoredMapSize();
        if (metaClaim != null) KingdomsConfig.warnClaimSizeMismatch(metaClaim);
        if (metaMap != null) KingdomsConfig.warnMapSizeMismatch(metaMap);

        context.msg(ChatColor.GREEN + "[SaberKingdoms] Reload terminé.");
    }


    private void reloadHard(KingdomCommandContext context) {

        // 1) Stopper proprement les guerres et UIs
        WarManager.stopAllAndClearAllUIs();

        // 2) S'assurer que la config existe (comme au boot)
        KingdomsConfig.ensureDefaultConfig(plugin);

        // 3) Charger la config -> settings (INTENTION ADMIN)
        KingdomsSettings newSettings = KingdomsConfigLoader.load(plugin);

        // 4) Appliquer la nouvelle géométrie (DESTRUCTIF)
        ClaimManager.hardRegenerateClaims(
                newSettings.claims().mapSize(),
                newSettings.claims().claimSize()
        );

        // 5) Swap atomique des settings runtime
        SettingsProvider.set(newSettings);

        context.msg(ChatColor.GREEN + "[SaberKingdoms] Reload HARD effectué.");
        context.msg(ChatColor.RED + "Tous les claims ont été régénérés.");
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "reload " + ChatColor.WHITE + "[hard]";
    }
}
