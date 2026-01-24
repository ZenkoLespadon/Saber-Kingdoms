package com.massivecraft.factions;

import cc.javajobs.wgbridge.WorldGuardBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kingdomspvp.kingdoms.commands.KingdomCommandExecutor;
import com.kingdomspvp.kingdoms.gui.WarDeclareWizard;
import com.kingdomspvp.kingdoms.listeners.*;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.kingdomspvp.kingdoms.services.WarRuntime;
import com.kingdomspvp.kingdoms.utils.*;
import com.kingdomspvp.kingdoms.utils.data.KingdomsConfig;
import com.kingdomspvp.kingdoms.utils.data.KingdomsConfigLoader;
import com.kingdomspvp.kingdoms.utils.data.LocalDateTimeAdapter;
import com.kingdomspvp.kingdoms.utils.data.SettingsProvider;
import com.massivecraft.factions.addon.AddonManager;
import com.massivecraft.factions.addon.FactionsAddon;
import com.massivecraft.factions.cmd.CmdAutoHelp;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.FCmdRoot;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.cmd.audit.FChestListener;
import com.massivecraft.factions.cmd.audit.FLogManager;
import com.massivecraft.factions.cmd.audit.FLogType;
import com.massivecraft.factions.cmd.chest.AntiChestListener;
import com.massivecraft.factions.cmd.reserve.ReserveAdapter;
import com.massivecraft.factions.cmd.reserve.ReserveObject;
import com.massivecraft.factions.data.helpers.FactionDataHelper;
import com.massivecraft.factions.listeners.*;
import com.massivecraft.factions.listeners.vspecific.ChorusFruitListener;
import com.massivecraft.factions.missions.MissionHandler;
import com.massivecraft.factions.missions.TributeInventoryHandler;
import com.massivecraft.factions.missions.impl.MissionHandlerModern;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.*;
import com.massivecraft.factions.util.adapters.*;
import com.massivecraft.factions.util.flight.FlightEnhance;
import com.massivecraft.factions.util.flight.stuct.AsyncPlayerMap;
import com.massivecraft.factions.util.timer.TimerManager;
import com.massivecraft.factions.zcore.CommandVisibility;
import com.massivecraft.factions.zcore.MPlugin;
import com.massivecraft.factions.zcore.file.impl.FileManager;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.Permissable;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import com.massivecraft.factions.zcore.frame.fupgrades.UpgradesListener;
import com.massivecraft.factions.zcore.util.ShutdownParameter;
import com.massivecraft.factions.zcore.util.StartupParameter;
import com.massivecraft.factions.zcore.util.TextUtil;
import me.lucko.commodore.CommodoreProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.*;


public class FactionsPlugin extends MPlugin {

    public static FactionsPlugin instance;
    private final Gson gsonSerializer = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().enableComplexMapKeySerialization().excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE)
            .registerTypeAdapter(new TypeToken<Map<Permissable, Map<PermissableAction, Access>>>() {
            }.getType(), new PermissionsMapTypeAdapter())
            .registerTypeAdapter(LazyLocation.class, new MyLocationTypeAdapter())
            .registerTypeAdapter(new TypeToken<Map<FLocation, Set<String>>>() {
            }.getType(), new MapFLocToStringSetTypeAdapter())
            .registerTypeAdapter(Inventory.class, new InventoryTypeAdapter())
            .registerTypeAdapter(ReserveObject.class, new ReserveAdapter())
            .registerTypeAdapter(Location.class, new LocationTypeAdapter())
            .registerTypeAdapterFactory(EnumTypeAdapter.ENUM_FACTORY)
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    //TODO REDO
    public static boolean cachedRadiusClaim;

    public static Permission perms = null;
    private Map<String, FactionsAddon> factionsAddonHashMap;
    private final HashMap<Faction, String> shieldStatMap = new HashMap<>();

    // This plugin sets the boolean true when fully enabled.
    // Plugins can check this boolean while hooking in have
    // a green light to use the api.
    public static boolean startupFinished = false;
    public boolean PlaceholderApi;

    // Commands
    public FCmdRoot cmdBase;
    public CmdAutoHelp cmdAutoHelp;
    public short version;
    public List<String> itemList = getConfig().getStringList("fchest.Items-Not-Allowed");
    public boolean hookedPlayervaults;
    public FLogManager fLogManager;
    public List<ReserveObject> reserveObjects;
    public FileManager fileManager;
    public TimerManager timerManager;
    private FactionsPlayerListener factionsPlayerListener;
    private boolean locked = false;
    private Integer AutoLeaveTask = null;
    private ClipPlaceholderAPIManager clipPlaceholderAPIManager;
    private boolean mvdwPlaceholderAPIManager = false;

    public String SERVER_TYPE;

    public FactionsPlugin() {
        instance = this;
    }

    public static FactionsPlugin getInstance() {
        return instance;
    }

    public static boolean canPlayersJoin() {
        return startupFinished;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public boolean getLocked() {
        return this.locked;
    }

    public void setLocked(boolean val) {
        this.locked = val;
        this.setAutoSave(val);
    }

    @Override
    public void onEnable() {

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Logger.print("You are missing dependencies!", Logger.PrefixType.FAILED);
            Logger.print("Please verify [Vault] is installed!", Logger.PrefixType.FAILED);
            Conf.save();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.version = Short.parseShort(
                ReflectionUtils.PackageType.getServerVersion().split("_")[1]
        );

        if (!preEnable()) {
            this.loadSuccessful = false;
            return;
        }

        // Load Conf from disk
        Conf.load();

        StartupParameter.initData(this, () -> {

            if (getConfig().getBoolean("enable-faction-flight", true)) {
                Bukkit.getServer().getScheduler().runTaskTimer(
                        FactionsPlugin.getInstance(),
                        new FlightEnhance(),
                        30L, 30L
                );
            }

            VersionProtocol.printVerionInfo();

            this.cmdBase = new FCmdRoot();
            this.cmdAutoHelp = new CmdAutoHelp();

            setupPermissions();

            if (Conf.worldGuardChecking || Conf.worldGuardBuildPriority) {
                Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (wg != null) {
                    new WorldGuardBridge().connect(this, true);
                }
            }

            startAutoLeaveTask(false);

            Bukkit.getPluginManager().registerEvents(new SaberGUIListener(), this);
            Bukkit.getPluginManager().registerEvents(
                    factionsPlayerListener = new FactionsPlayerListener(), this
            );

            if (Conf.userSpawnerChunkSystem) {
                Bukkit.getPluginManager().registerEvents(
                        new SpawnerChunkListener(), this
                );
            }

            if (FactionsPlugin.getInstance().getConfig()
                    .getBoolean("disable-chorus-teleport-in-territory")
                    && this.version > 8) {
                Bukkit.getPluginManager().registerEvents(
                        new ChorusFruitListener(), this
                );
            }

            FactionDataHelper.init();

            if (version > 8) {
                Bukkit.getPluginManager().registerEvents(
                        new MissionHandlerModern(), this
                );
            }

            for (Listener listener : new Listener[]{
                    new TributeInventoryHandler(),
                    new FactionsEntityListener(),
                    new FactionsExploitListener(),
                    new FactionsBlockListener(),
                    new UpgradesListener(),
                    new MissionHandler(this),
                    new FChestListener(),
                    new MenuListener(),
                    new AntiChestListener()
            }) {
                Bukkit.getPluginManager().registerEvents(listener, this);
            }

            if (Conf.useGraceSystem) {
                Bukkit.getPluginManager().registerEvents(
                        timerManager.graceTimer, this
                );
            }

            new AsyncPlayerMap(this);

            this.setupPlaceholderAPI();
            factionsAddonHashMap = new HashMap<>();
            AddonManager.getAddonManagerInstance().loadAddons();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!factionsAddonHashMap.isEmpty()) {
                    FCmdRoot.instance.addVariableCommands();
                    FCmdRoot.instance.rebuild();
                }
            }, 100L);

            this.getCommand(refCommand).setExecutor(cmdBase);
            if (!CommodoreProvider.isSupported()) {
                this.getCommand(refCommand).setTabCompleter(this);
            }

            this.postEnable();
            this.loadSuccessful = true;
            FactionsPlugin.startupFinished = true;
        });

        // =========================
        // KINGDOMS (SYNC AFTER BOOT)
        // =========================
        Bukkit.getScheduler().runTask(this, () -> {

            try {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    WarManager.clearSidebarFor(p);
                }

                initKingdomsSoft();

            } catch (Exception e) {
                e.printStackTrace();
            }

            SERVER_TYPE = SettingsProvider.get().serverType();

            Bukkit.getLogger().info("[Kingdoms] Server type: " + SERVER_TYPE);


            PluginManager pm = getServer().getPluginManager();

            for (Listener listener : new Listener[] {
                    new KingdomProtectionListener(),
                    new WarDeclareWizard(),
                    new WarJoinAnnounceListener(),
                    new WarUIReconnectListener(),
                    new TablistKingdomColors(this, 1200L),
                    new AutoClaimListener(),
                    new KingdomChatFormatListener(),
                    new KingdomNpcProtectionListener(this)
            }) {
                pm.registerEvents(listener, this);
            }



            TablistKingdomColors.refreshAll();

            KingdomCommandExecutor exec =
                    new KingdomCommandExecutor(FactionsPlugin.getInstance());
            this.getCommand("k").setExecutor(exec);
            this.getCommand("k").setTabCompleter(exec);
        });
    }

    private void initKingdomsSoft() {

        KingdomsConfig.ensureDefaultConfig(this);

        if (!KingdomsConfig.load(this)) {
            getLogger().severe("Erreur de chargement de la configuration Kingdoms.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SettingsProvider.set(
                KingdomsConfigLoader.load(this)
        );

        KingdomsManager.loadKingdoms(success -> {
            if (Boolean.TRUE.equals(success)) {
                getLogger().info("Kingdoms loaded");
            }
        });

        ClaimManager.loadClaims(success -> {
            if (Boolean.TRUE.equals(success)) {
                if (ClaimManager.getNumClaims() == 0) {
                    ClaimManager.generateClaims();
                    ClaimManager.saveClaims();
                    getLogger().info("Claims generated and saved because empty");
                } else {
                    getLogger().info("Claims loaded from disk");
                }
            }
        });

        WarManager.loadWars(success -> {
            if (success) {
                getLogger().info("Wars loaded");
            }
        });

        getLogger().info("Nombre de claims : " + ClaimManager.getNumClaims());

        WarManager.reloadSettings();
        WarRuntime.reloadSettings();
        WarKDAListener.reloadSettings();
        WarBlockEditListener.reloadSettings();
        WarDeathListener.reloadSettings();
        ClaimVisualization.reloadSettings();
    }




    private void setupPlaceholderAPI() {
        Plugin clip = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (clip != null && clip.isEnabled()) {
            this.clipPlaceholderAPIManager = new ClipPlaceholderAPIManager();
            if (this.clipPlaceholderAPIManager.register()) {
                PlaceholderApi = true;
                Logger.print("Successfully registered placeholders with PlaceholderAPI.", Logger.PrefixType.DEFAULT);
            } else {
                PlaceholderApi = false;
            }
        } else {
            PlaceholderApi = false;
        }

        Plugin mvdw = Bukkit.getPluginManager().getPlugin("MVdWPlaceholderAPI");
        if (mvdw != null && mvdw.isEnabled()) {
            this.mvdwPlaceholderAPIManager = true;
            Logger.print("Found MVdWPlaceholderAPI. Adding hooks.", Logger.PrefixType.DEFAULT);
        }
    }


    public HashMap<Faction, String> getShieldStatMap() {
        return shieldStatMap;
    }

    public Map<String, FactionsAddon> getFactionsAddonHashMap() {
        return factionsAddonHashMap;
    }

    public boolean isClipPlaceholderAPIHooked() {
        return this.clipPlaceholderAPIManager != null;
    }

    public boolean isMVdWPlaceholderAPIHooked() {
        return this.mvdwPlaceholderAPIManager;
    }

    private void setupPermissions() {
        try {
            RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
            if (rsp != null) perms = rsp.getProvider();
        } catch (NoClassDefFoundError ignored) {
        }
    }

    @Override
    public Gson getGson() {
        return this.gsonSerializer;
    }

    @Override
    public void onDisable() {
        ShutdownParameter.initShutdown(this);

        if (this.AutoLeaveTask != null) {
            getServer().getScheduler().cancelTask(this.AutoLeaveTask);
            this.AutoLeaveTask = null;
        }
        if (TextUtil.AUDIENCES != null) {
            TextUtil.AUDIENCES.close();
        }

        WarManager.stopAllAndClearAllUIs();

        KingdomsManager.saveKingdoms();
        ClaimManager.saveClaims();
        // TODO : Mettre une classe dans WarManager pour sauvegarder les guerres

        super.onDisable();
    }

    public void startAutoLeaveTask(boolean restartIfRunning) {
        if (AutoLeaveTask != null) {
            if (!restartIfRunning) return;
            this.getServer().getScheduler().cancelTask(AutoLeaveTask);
        }

        if (Conf.useAutoLeaveAndDisbandSystem) {
            if (Conf.autoLeaveRoutineRunsEveryXMinutes > 0.0) {
                long ticks = (long) (20 * 60 * Conf.autoLeaveRoutineRunsEveryXMinutes);
                AutoLeaveTask = getServer().getScheduler().scheduleSyncRepeatingTask(this, new AutoLeaveTask(), ticks, ticks);
            }
        }
    }

    @Override
    public void postAutoSave() {
        Conf.save();
    }


    public Economy getEcon() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp.getProvider();
    }


    @Override
    public boolean logPlayerCommands() {
        return Conf.logPlayerCommands;
    }

    @Override
    public boolean handleCommand(CommandSender sender, String commandString, boolean testOnly) {
        return sender instanceof Player && FactionsPlayerListener.preventCommand(commandString, (Player) sender) || super.handleCommand(sender, commandString, testOnly);
    }


    // This method must stay for < 1.12 versions
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Must be a LinkedList to prevent UnsupportedOperationException.
        List<String> argsList = new LinkedList<>(Arrays.asList(args));
        CommandContext context = new CommandContext(sender, argsList, alias);
        List<FCommand> commandsList = cmdBase.getSubCommands();
        FCommand commandsEx = cmdBase;
        List<String> completions = new ArrayList<>();
        // Check for "" first arg because spigot is mangled.
        if (context.args.get(0).equals("")) {
            for (FCommand subCommand : commandsEx.getSubCommands()) {
                if (subCommand.getRequirements().isPlayerOnly() && sender.hasPermission(subCommand.getRequirements().getPermission().node) && subCommand.getVisibility() != CommandVisibility.INVISIBLE)
                    completions.addAll(subCommand.getAliases());
            }
            return completions;
        } else if (context.args.size() == 1) {
            for (; !commandsList.isEmpty() && !context.args.isEmpty(); context.args.remove(0)) {
                String cmdName = context.args.get(0).toLowerCase();
                boolean toggle = false;
                for (FCommand fCommand : commandsList) {
                    for (String s : fCommand.getAliases()) {
                        if (s.startsWith(cmdName)) {
                            commandsList = fCommand.getSubCommands();
                            completions.addAll(fCommand.getAliases());
                            toggle = true;
                            break;
                        }
                    }
                    if (toggle) break;
                }
            }
            String lastArg = args[args.length - 1].toLowerCase();
            List<String> filteredCompletions = new ArrayList<>(completions.size());
            for (String completion : completions) {
                if (completion.toLowerCase().startsWith(lastArg)) {
                    filteredCompletions.add(completion);
                }
            }
            return filteredCompletions;
        } else {
            String lastArg = args[args.length - 1].toLowerCase();
            for (Role value : Role.VALUES) completions.add(value.nicename);
            for (Relation value : Relation.VALUES) completions.add(value.nicename);
            // The stream and foreach from the old implementation looped 2 times, by looping all players -> filtered -> looped filter and added -> filtered AGAIN at the end.
            // This loops them once and just adds, because we are filtering the arguments at the end anyways
            for (Player player : Bukkit.getServer().getOnlinePlayers()) completions.add(player.getName());
            for (Faction faction : Factions.getInstance().getAllFactions())
                completions.add(ChatColor.stripColor(faction.getTag()));
            List<String> filteredCompletions = new ArrayList<>(completions.size());
            for (String completion : completions) {
                if (completion.toLowerCase().startsWith(lastArg)) {
                    filteredCompletions.add(completion);
                }
            }
            return filteredCompletions;
        }
    }

    // -------------------------------------------- //
    // Functions for other plugins to hook into
    // -------------------------------------------- //

    // If another plugin is handling insertion of chat tags, this should be used to notify Factions
    public void handleFactionTagExternally(boolean notByFactions) {
        Conf.chatTagHandledByAnotherPlugin = notByFactions;
    }

    public FLogManager getFlogManager() {
        return fLogManager;
    }

    public void logFactionEvent(Faction faction, FLogType type, String... arguments) {
        this.fLogManager.log(faction, type, arguments);
    }


    public List<ReserveObject> getFactionReserves() {
        return this.reserveObjects;
    }


    public String getPrimaryGroup(OfflinePlayer player) {
        return perms == null || !perms.hasGroupSupport() ? " " : perms.getPrimaryGroup(Bukkit.getWorlds().get(0).toString(), player);
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }


    public FactionsPlayerListener getFactionsPlayerListener() {
        return this.factionsPlayerListener;
    }
}