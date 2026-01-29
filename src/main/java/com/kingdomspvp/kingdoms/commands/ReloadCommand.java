// src/main/java/com/kingdomspvp/kingdoms/commands/ReloadCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.listeners.WarBlockEditListener;
import com.kingdomspvp.kingdoms.listeners.WarDeathListener;
import com.kingdomspvp.kingdoms.listeners.WarKDAListener;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.kingdomspvp.kingdoms.utils.*;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.utils.data.KingdomsConfig;
import com.kingdomspvp.kingdoms.utils.data.KingdomsConfigLoader;
import com.kingdomspvp.kingdoms.utils.data.KingdomsSettings;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
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

        this.permission = "kingdoms.admin.reload";
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (!hasPermission(context.sender)) {
            context.msg(ChatColor.RED + "Vous n'avez pas la permission.");
            return;
        }

        boolean hard = !context.args.isEmpty()
                && context.args.get(0).equalsIgnoreCase("hard");

        reload(context, hard);
    }



    private void reload(KingdomCommandContext context, boolean hard) {

        // 0) HARD → arrêt propre AVANT tout
        if (hard) {
            WarManager.stopAllAndClearAllUIs();
        }

        // 1) S'assurer que la config existe
        KingdomsConfig.ensureDefaultConfig(plugin);

        // 2) Charger la config (intention admin)
        KingdomsSettings newSettings = KingdomsConfigLoader.load(plugin);

        // 3) HARD → appliquer changements destructifs
        if (hard) {
            ClaimManager.hardRegenerateClaims(
                    newSettings.claims().mapSize(),
                    newSettings.claims().claimSize()
            );
        } else {
            // SOFT → juste warnings si mismatch
            Integer metaClaim = ClaimManager.getStoredClaimSize();
            Integer metaMap   = ClaimManager.getStoredMapSize();
            if (metaClaim != null) KingdomsConfig.warnClaimSizeMismatch(metaClaim);
            if (metaMap != null)   KingdomsConfig.warnMapSizeMismatch(metaMap);
        }

        // 4) Swap atomique des settings runtime
        SettingsProvider.set(newSettings);

        // 5) Recharger les systèmes runtime
        WarManager.reloadSettings();
        WarRuntime.reloadSettings();
        WarKDAListener.reloadSettings();
        WarBlockEditListener.reloadSettings();
        WarDeathListener.reloadSettings();
        ClaimVisualization.reloadSettings();
        //ClaimsVizCommand se reload dans le perform()
        ClaimManager.reloadSettings();
        KingdomsManager.reloadSettings();


        // 6) Feedback admin
        context.msg(ChatColor.GREEN + "[SaberKingdoms] Reload "
                + (hard ? "HARD" : "soft") + " terminé.");

        if (hard) {
            context.msg(ChatColor.RED + "Tous les claims ont été régénérés.");
        }
    }


    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "reload " + ChatColor.WHITE + "[hard]";
    }
}
