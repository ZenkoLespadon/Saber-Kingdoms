// src/main/java/com/kingdomspvp/kingdoms/commands/ReloadCommand.java
package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.utils.KingdomsConfig;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.WarManager;
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

        // 1) Comme au démarrage serveur
        KingdomsConfig.ensureDefaultConfig(plugin);

        if (!KingdomsConfig.load(plugin)) {
            context.msg(ChatColor.RED + "[SaberKingdoms] Erreur chargement config.");
            return;
        }

        // 2) Recharger claims (META = runtime)
        ClaimManager.loadClaims(ok -> {
            if (!ok) {
                context.msg(ChatColor.RED + "[SaberKingdoms] Erreur chargement claims.");
                return;
            }

            Integer metaClaim = ClaimManager.getStoredClaimSize();
            Integer metaMap   = ClaimManager.getStoredMapSize();

            if (metaClaim != null) {
                KingdomsConfig.warnClaimSizeMismatch(metaClaim);
            }
            if (metaMap != null) {
                KingdomsConfig.warnMapSizeMismatch(metaMap);
            }

            applyRuntimeConfig();

            context.msg(ChatColor.GREEN + "[SaberKingdoms] Reload effectué.");
        });
    }



    private void reloadHard(KingdomCommandContext context) {

        WarManager.stopAllAndClearAllUIs();

        KingdomsConfig.ensureDefaultConfig(plugin);

        if (!KingdomsConfig.load(plugin)) {
            context.msg(ChatColor.RED + "[SaberKingdoms] Erreur chargement config.");
            return;
        }

        ClaimManager.hardRegenerateClaims();

        applyRuntimeConfig();

        context.msg(ChatColor.GREEN + "[SaberKingdoms] Reload HARD : claims régénérés.");
    }



    private void applyRuntimeConfig() {
        // Il manque target-fraction, ratio min max (war), tous les variables de ClaimsVizCommand

        // ClaimsVizCommand lit periodTicks/seconds dans sa logique -> si tu veux, branche sur KingdomsConfig dans cette commande

        // --- ClaimVisualization ---
        // Reco: remplace tes "static final" par des setters ou des champs non-final.
        // Exemple (à faire côté ClaimVisualization) :

        // ClaimVisualization.setDustSize(KingdomsConfig.DUST_SIZE);
        // ClaimVisualization.setStep(KingdomsConfig.STEP);
        // ClaimVisualization.setPeriodTicks(KingdomsConfig.CLAIM_VIZ_PERIOD_TICKS);
        // ClaimVisualization.setCountPerSpawn(KingdomsConfig.CLAIM_VIZ_COUNT_PER_SPAWN);
        // ClaimVisualization.setMaxSpawnsPerTick(KingdomsConfig.CLAIM_VIZ_MAX_SPAWNS_PER_TICK);
        // ClaimVisualization.setMaxDistanceBlocks(KingdomsConfig.CLAIM_VIZ_MAX_DISTANCE_BLOCKS);

        // ClaimsVizCommand.SetDefaultSeconds(KingdomsConfig.CLAIM_VIZ_COMMAND_DEFAULT_SECONDS);
        // ClaimsVizCommand.SetMinSeconds(KingdomsConfig.CLAIM_VIZ_COMMAND_CLAIM_VIZ_MIN_SECONDS);
        // CLaimsVizCommand.SetMaxSeconds(KingdomsConfig.CLAIM_VIZ_COMMAND_CLAIM_VIZ_MAX_SECONDS);
        // ClaimsVizCommand.SetPeriodTicks(KingdomsConfig.CLAIM_VIZ_COMMAND_CLAIM_VIZ_PERIOD_TICKS);



        // --- WarManager / WarRuntime ---
        // Idem: remplacer finals / constantes par champs modifiables.


        // WarManager.TEST_MODE = KingdomsConfig.WAR_TEST_MODE;  (si tu exposes un setter)
        // WarManager.MIN_TIME_BEFORE_WAR = Duration.ofMinutes(KingdomsConfig.WAR_MIN_TIME_MIN);
        // WarManager.MAX_TIME_BEFORE_WAR = Duration.ofHours(KingdomsConfig.WAR_MAX_TIME_HOURS);
        // WarManager.JOIN_PROMPT_LEAD_TIME = Duration.ofSeconds(KingdomsConfig.WAR_JOIN_PROMPT_LEAD_SEC);
        // WarManager.DETECTION_WINDOW = Duration.ofSeconds(KingdomsConfig.WAR_DETECTION_WINDOW_SEC);
        // WarManager.

        // WarRuntime.WAR_DURATION_SECONDS = KingdomsConfig.WAR_DURATION_SEC;
        // WarRuntime.setMaxRounds(KingdomsConfig.WAR_MAX_ROUNDS);
        // WarRuntime.setRoundGainFactor(KingdomsConfig.WAR_ROUND_GAIN_FACTOR);
        // WarRuntime.setTargetPoints(KingdomsConfig.WAR_TARGET_POINTS);
        // WarRuntime.setTargetFraction(KingdomsConfig.WAR_TARGET_FRACTION);

        // Teleport
        // WarManager.setTeleportOffsetFromBorder(KingdomsConfig.TP_OFFSET_FROM_BORDER);
        // WarManager.setTeleportYOffset(KingdomsConfig.TP_Y_OFFSET);

        // KDA
        // WarKDAListener.setAssistWindowSeconds(KingdomsConfig.KDA_ASSIST_WINDOW_SEC);
        // WarKDAListener.setCountSelfDamage(KingdomsConfig.KDA_COUNT_SELF_DAMAGE);

        // Blocks
        // WarBlockEditListener.setRevertAfterSeconds(KingdomsConfig.BLOCK_REVERT_AFTER_SEC);
        // WarBlockEditListener.setForbiddenMaterials(KingdomsConfig.BLOCKS_FORBIDDEN);

        // Death
        // WarDeathListener.setTpMessageDelaySeconds(KingdomsConfig.TP_AFTER_DEATH_DELAY_SEC);
    }

    @Override
    public String getUsageTranslation() {
        return ChatColor.GREEN + "reload " + ChatColor.WHITE + "[hard]";
    }
}
