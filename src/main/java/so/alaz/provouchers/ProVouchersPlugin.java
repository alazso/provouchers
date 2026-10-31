package so.alaz.provouchers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import so.alaz.provouchers.antidupe.DupeDetector;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.api.VoucherService;
import so.alaz.provouchers.command.Diagnostics;
import so.alaz.provouchers.command.StashCommand;
import so.alaz.provouchers.command.VoucherCommand;
import so.alaz.provouchers.comficonf.ComfiConfIntegration;
import so.alaz.provouchers.condition.ConditionRegistry;
import so.alaz.provouchers.config.ConfigFile;
import so.alaz.provouchers.config.ConfigManager;
import so.alaz.provouchers.config.LiveSettings;
import so.alaz.provouchers.migrate.CrazyVouchersMigrator;
import so.alaz.provouchers.migrate.MigrationService;
import so.alaz.provouchers.service.VoucherServiceImpl;
import org.bukkit.configuration.ConfigurationSection;
import so.alaz.provouchers.cooldown.CooldownService;
import so.alaz.provouchers.cooldown.CooldownTiers;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.give.VoucherGiveService;
import so.alaz.provouchers.stash.StashService;
import so.alaz.provouchers.util.Durations;
import so.alaz.provouchers.gui.GuiListener;
import so.alaz.provouchers.gui.GuiManager;
import so.alaz.provouchers.hook.EconomyHook;
import so.alaz.provouchers.hook.HeadDatabaseItemHook;
import so.alaz.provouchers.hook.Hook;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.hook.ItemHook;
import so.alaz.provouchers.hook.ItemsAdderItemHook;
import so.alaz.provouchers.hook.LuckPermsPermissionHook;
import so.alaz.provouchers.hook.NexoItemHook;
import so.alaz.provouchers.hook.OraxenItemHook;
import so.alaz.provouchers.hook.PermissionHook;
import so.alaz.provouchers.hook.RegionHook;
import so.alaz.provouchers.hook.VaultEconomyHook;
import so.alaz.provouchers.hook.WorldGuardRegionHook;
import so.alaz.provouchers.gui.ConfirmGui;
import so.alaz.provouchers.gui.FromhandGui;
import so.alaz.provouchers.gui.PreviewGui;
import so.alaz.provouchers.gui.RewardPreviewGui;
import so.alaz.provouchers.gui.StashGui;
import so.alaz.provouchers.gui.VoucherAdminMenu;
import so.alaz.provouchers.listener.CooldownLoadListener;
import so.alaz.provouchers.listener.FireworkGuardListener;
import so.alaz.provouchers.listener.FirstJoinListener;
import so.alaz.provouchers.listener.SoulboundListener;
import so.alaz.provouchers.listener.StashJoinListener;
import so.alaz.provouchers.listener.VoucherInteractListener;
import so.alaz.provouchers.listener.VoucherStationListener;
import so.alaz.provouchers.metrics.MetricCounters;
import so.alaz.provouchers.metrics.Metrics;
import so.alaz.provouchers.metrics.ErrorReports;
import so.alaz.provouchers.metrics.VoucherMetrics;
import so.alaz.provouchers.platform.CooldownManager;
import so.alaz.provouchers.platform.DiscordWebhook;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.redeem.RewardExecutor;
import so.alaz.provouchers.storage.Backend;
import so.alaz.provouchers.storage.StorageConfig;
import so.alaz.provouchers.storage.StorageProvider;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.ItemResolver;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

import static net.kyori.adventure.text.Component.text;

/**
 * Plugin entry point. Verifies the Java 25 runtime requirement, opens storage,
 * loads voucher and code definitions, and wires up the redeem pipeline,
 * listeners, GUI, metrics, and command.
 */
public final class ProVouchersPlugin extends JavaPlugin {

    private static final int MINIMUM_JAVA_FEATURE = 25;

    /** How long disabling waits for the redeem verifications still touching storage, and again for Stash claims. */
    private static final Duration SETTLE_WAIT = Duration.ofSeconds(3);

    /** How long disabling waits for the storage pool to close. */
    private static final long STORAGE_CLOSE_TIMEOUT_SECONDS = 10;

    /** Name of the ComfiConf bridge plugin. Written out here: the API's constant would load an API class. */
    private static final String COMFICONF = "ComfiConf";

    /** The config.yml settings that apply without a restart; replaced on every reload. */
    private final AtomicReference<LiveSettings> liveSettings = new AtomicReference<>();

    private VoucherStorage storage;
    /** The ComfiConf registration of config.yml; null while none is registered. */
    private ComfiConfHook comfiConf;
    private Metrics metrics;
    private GuiManager guiManager;
    private DiscordWebhook discordWebhook;
    private VoucherGiveService giveService;
    private RedeemHandler redeemHandler;
    private StashService stashService;

    @Override
    public void onEnable() {
        if (Runtime.version().feature() < MINIMUM_JAVA_FEATURE) {
            disableWith("ProVouchers requires Java " + MINIMUM_JAVA_FEATURE + " or newer, but the server "
                + "is running Java " + Runtime.version().feature() + ".");
            return;
        }
        saveDefaultConfig();
        liveSettings.set(LiveSettings.from(getConfig()));
        saveResourceIfMissing("vouchers/example.yml");
        saveResourceIfMissing("codes/example.yml");

        Backend backend = parseBackend(getConfig().getString("storage.backend", "sqlite"));
        storage = new VoucherStorage(new StorageProvider(buildStorageConfig(backend)));
        storage.init().whenComplete((ignored, error) -> {
            if (error != null) {
                getComponentLogger().error(text("Failed to open ProVouchers storage; duplicate and code "
                    + "tracking will be unavailable until this is fixed.", NamedTextColor.RED), error);
            }
        });

        VoucherRegistry registry = new VoucherRegistry();
        Text text = new Text();
        for (String lang : new String[] {"en", "de", "fr", "es", "pl", "da", "nl"}) {
            saveResourceIfMissing("lang/" + lang + ".yml");
        }
        Messages messages = new Messages(getDataFolder(),
            getConfig().getString("locale.default", "en"),
            getConfig().getBoolean("locale.per-player", true));
        HookRegistry hooks = buildHooks();
        ItemResolver itemResolver = new ItemResolver(hooks);
        ConditionRegistry conditionRegistry = new ConditionRegistry(text, hooks);
        ConfigManager configManager = new ConfigManager(getDataFolder(), registry, conditionRegistry, itemResolver);
        reportErrors(configManager.reload());

        Scheduler scheduler = new Scheduler(this);
        guiManager = new GuiManager(this);

        VoucherStamp stamp = new VoucherStamp(this);
        VoucherItemFactory factory = new VoucherItemFactory(text, stamp, itemResolver);
        discordWebhook = new DiscordWebhook();
        RewardExecutor rewardExecutor = new RewardExecutor(
            scheduler, text, itemResolver, factory, hooks,
            getComponentLogger(), discordWebhook);
        giveService = new VoucherGiveService(factory, scheduler, getComponentLogger());
        PreviewGui previewGui = new PreviewGui(registry, factory, giveService,
            new VoucherAdminMenu(text), guiManager, scheduler,
            text);
        RewardPreviewGui rewardPreviewGui = new RewardPreviewGui(factory, guiManager, scheduler, text);

        CooldownService cooldowns = new CooldownService(
            new CooldownManager(), storage, scheduler);

        MetricCounters counters = new MetricCounters();

        redeemHandler = new RedeemHandler(
            registry,
            stamp,
            new DupeDetector(storage),
            storage,
            rewardExecutor,
            scheduler,
            text,
            messages,
            cooldowns,
            conditionRegistry,
            counters,
            getComponentLogger(),
            loadCooldownTiers(),
            new ConfirmGui(factory, guiManager, scheduler, text),
            liveSettings::get
        );

        Duration stashExpiry = Durations.parseOrNull(getConfig().getString("stash.expire-after", ""));
        stashService = new StashService(storage, registry, redeemHandler, scheduler, text,
            messages, getLogger(), counters, stashExpiry != null ? stashExpiry.toMillis() : 0L);
        boolean stashEnabled = getConfig().getBoolean("stash.enabled", true);
        if (stashEnabled) {
            StashGui stashGui = new StashGui(stashService, registry, factory, guiManager, scheduler, text,
                messages, liveSettings::get);
            new StashCommand(stashGui, text, messages)
                .register(this, getConfig().getStringList("stash.command-aliases"));
            if (getConfig().getBoolean("stash.notify-on-join", true)) {
                getServer().getPluginManager().registerEvents(
                    new StashJoinListener(stashService, scheduler, text, messages), this);
            }
            scheduler.repeatingAsync(stashService::pruneExpired, 60, 600, TimeUnit.SECONDS);
            // The overflow policy is read on every give, so /voucher reload applies a change.
            giveService.overflowPolicy(stashService,
                () -> "stash".equalsIgnoreCase(getConfig().getString("stash.overflow", "drop")));
        }

        getServer().getPluginManager().registerEvents(
            new VoucherInteractListener(stamp, redeemHandler, registry, rewardPreviewGui,
                () -> liveSettings.get().leftClickPreview()), this);
        getServer().getPluginManager().registerEvents(new CooldownLoadListener(cooldowns), this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);
        getServer().getPluginManager().registerEvents(new VoucherStationListener(stamp), this);
        getServer().getPluginManager().registerEvents(new FireworkGuardListener(), this);
        getServer().getPluginManager().registerEvents(
            new SoulboundListener(stamp, registry, text, messages), this);
        List<String> firstJoin = getConfig().getStringList("auto-give.first-join");
        if (!firstJoin.isEmpty()) {
            getServer().getPluginManager().registerEvents(
                new FirstJoinListener(registry, giveService, firstJoin, getComponentLogger()), this);
        }
        getServer().getOnlinePlayers().forEach(player -> cooldowns.hydrate(player.getUniqueId()));
        // The metrics handle is read live, since metrics start after the command is registered.
        Diagnostics diagnostics = new Diagnostics(this, storage, registry, hooks, messages,
            () -> backend.name().toLowerCase(Locale.ROOT), () -> metrics, this::comfiConfStatus,
            getConfig().getBoolean("metrics.enabled", true));
        FromhandGui fromhandGui = new FromhandGui(guiManager, configManager, messages, text, getDataFolder());
        MigrationService migrationService = new MigrationService(
            new CrazyVouchersMigrator(getDataFolder(), registry));
        new VoucherCommand(registry, giveService, redeemHandler, configManager, previewGui, text,
            messages, diagnostics, storage, scheduler, fromhandGui, migrationService, stashService,
            this::onConfigReloaded)
            .register(this, getConfig().getStringList("command-aliases"));

        getServer().getServicesManager().register(VoucherService.class,
            new VoucherServiceImpl(registry, giveService, stashService), this,
            ServicePriority.Normal);

        metrics = VoucherMetrics.start(this, registry, counters,
            () -> backend.name().toLowerCase(Locale.ROOT));
        ErrorReports.install(metrics);

        hookComfiConf();

        getComponentLogger().info(text("ProVouchers enabled with " + registry.voucherCount()
            + " voucher(s) and " + registry.codeCount() + " code(s).", NamedTextColor.GOLD));
    }

    @Override
    public void onDisable() {
        ComfiConfHook registration = comfiConf;
        if (registration != null) {
            comfiConf = null;
            closeQuietly("unregister from ComfiConf", registration::close);
        }
        if (guiManager != null) {
            closeQuietly("close open menus", guiManager::closeAll);
        }
        ErrorReports.clear();
        if (metrics != null) {
            closeQuietly("stop metrics", metrics::shutdown);
        }
        // Settling pending work writes to storage, so it must run before storage closes.
        if (giveService != null) {
            closeQuietly("settle undelivered gives", giveService::settleForShutdown);
        }
        if (redeemHandler != null) {
            closeQuietly("settle pending redeems", () -> redeemHandler.settleForShutdown(SETTLE_WAIT));
        }
        if (stashService != null) {
            closeQuietly("settle pending Stash claims", () -> stashService.settleForShutdown(SETTLE_WAIT));
        }
        if (storage != null) {
            closeQuietly("close storage",
                () -> storage.shutdown().get(STORAGE_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        if (discordWebhook != null) {
            closeQuietly("close the webhook client", discordWebhook::close);
        }
        getComponentLogger().info(text("ProVouchers disabled.", NamedTextColor.GOLD));
    }

    /** One shutdown step, which may fail with any exception. */
    @FunctionalInterface
    private interface ShutdownStep {
        void run() throws Exception;
    }

    /** Runs a shutdown step, logging (not propagating) any failure so later steps still run. */
    private void closeQuietly(String what, ShutdownStep step) {
        try {
            step.run();
        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            getComponentLogger().warn(text("ProVouchers: failed to " + what + " during shutdown: "
                + t, NamedTextColor.RED));
            ErrorReports.report("shutdown", t);
        }
    }

    /**
     * Registers every integration provider unconditionally. Each hook detects its backing plugin and
     * reports availability per call; a hook whose plugin is missing is skipped at resolution time.
     */
    private HookRegistry buildHooks() {
        HookRegistry hooks = new HookRegistry();
        register(hooks, EconomyHook.class, VaultEconomyHook::new);
        register(hooks, PermissionHook.class, LuckPermsPermissionHook::new);
        register(hooks, RegionHook.class, WorldGuardRegionHook::new);
        register(hooks, ItemHook.class, ItemsAdderItemHook::new);
        register(hooks, ItemHook.class, OraxenItemHook::new);
        register(hooks, ItemHook.class, NexoItemHook::new);
        register(hooks, ItemHook.class, HeadDatabaseItemHook::new);
        return hooks;
    }

    /**
     * Registers one provider, logging and skipping it when building it fails, so a broken third-party
     * plugin costs that integration rather than this plugin's enable.
     */
    private <T extends Hook> void register(HookRegistry hooks, Class<T> type, Supplier<T> provider) {
        try {
            hooks.register(type, provider.get());
        } catch (LinkageError | RuntimeException ex) {
            getComponentLogger().warn(text("ProVouchers: an integration provider for "
                + type.getSimpleName() + " could not be built and stays off: " + ex, NamedTextColor.RED));
        }
    }

    /**
     * Registers config.yml with ComfiConf when its bridge is enabled. The plugin keeps working in every branch: no
     * class loaded before this call names a ComfiConf API type.
     */
    private void hookComfiConf() {
        if (!getServer().getPluginManager().isPluginEnabled(COMFICONF)) {
            getLogger().info("ComfiConf is not installed; settings are edited in config.yml only");
            return;
        }
        Path dataFolder = getDataFolder().toPath();
        try {
            comfiConf = ComfiConfIntegration.register(this, dataFolder.resolve("config.yml"),
                dataFolder.resolve("backups"), this::reloadMainConfig);
            getLogger().info("Registered " + comfiConf.resourceId() + " with ComfiConf");
        } catch (LinkageError e) {
            Plugin bridge = getServer().getPluginManager().getPlugin(COMFICONF);
            getLogger().warning(incompatibleBridgeMessage(
                bridge == null ? "?" : bridge.getPluginMeta().getVersion(), e));
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Could not register with ComfiConf; continuing without it", e);
        }
    }

    /** Warning for an installed ComfiConf whose classes do not match the API this plugin was compiled against. */
    static String incompatibleBridgeMessage(String bridgeVersion, LinkageError error) {
        String missing = error.getMessage() == null
            ? error.getClass().getSimpleName()
            : "missing " + error.getMessage().replace('/', '.');
        return "The installed ComfiConf " + bridgeVersion + " does not include the ComfiConf API this plugin needs ("
            + missing + "). Update ComfiConf to the version this plugin was built for"
            + " (https://docs.comficonf.com/bridge/updating#when-a-plugin-needs-a-newer-bridge); settings are"
            + " edited in config.yml only until then.";
    }

    /**
     * Re-reads config.yml into the copy {@link #getConfig()} returns and applies the settings that need no restart.
     * Returns false and keeps the loaded copy when the file is missing or does not parse, which
     * {@link #reloadConfig()} alone would hide by loading an empty configuration. Runs on the server main thread.
     */
    private boolean reloadMainConfig() {
        String unreadable = ConfigFile.unreadableReason(new File(getDataFolder(), "config.yml"));
        if (unreadable != null) {
            getLogger().warning("config.yml could not be read, so the settings in effect stay active: " + unreadable);
            return false;
        }
        reloadConfig();
        liveSettings.set(LiveSettings.from(getConfig()));
        return true;
    }

    /**
     * What ComfiConf is doing, for {@code /voucher doctor}: the registered resource, the bridge installed without a
     * registration (its classes were unreachable at enable, or registering failed), or no bridge at all.
     */
    private String comfiConfStatus() {
        ComfiConfHook registration = comfiConf;
        if (registration != null && registration.isRegistered()) {
            return "<white>" + registration.resourceId() + " <green>(registered)";
        }
        return getServer().getPluginManager().isPluginEnabled(COMFICONF)
            ? "<yellow>installed, not registered"
            : "<gray>not installed";
    }

    /**
     * Applies config.yml for {@code /voucher reload}, then tells ComfiConf that the file changed outside it, so open
     * editors load the newer values. Returns false when the file could not be read and nothing was applied.
     */
    private boolean onConfigReloaded() {
        if (!reloadMainConfig()) {
            return false;
        }
        ComfiConfHook registration = comfiConf;
        if (registration != null) {
            registration.notifyChanged();
        }
        return true;
    }

    /** The {@code cooldown.tiers} config section as tier-name to multiplier; read once at enable. */
    private CooldownTiers loadCooldownTiers() {
        ConfigurationSection section = getConfig().getConfigurationSection("cooldown.tiers");
        if (section == null) {
            return CooldownTiers.none();
        }
        Map<String, Double> tiers = new HashMap<>();
        for (String tier : section.getKeys(false)) {
            tiers.put(tier.toLowerCase(Locale.ROOT), section.getDouble(tier, 1.0));
        }
        return new CooldownTiers(tiers);
    }

    private StorageConfig buildStorageConfig(Backend backend) {
        if (backend == Backend.SQLITE) {
            return StorageConfig.sqlite(new File(getDataFolder(), "data.db").getAbsolutePath());
        }
        String host = getConfig().getString("storage.host", "localhost");
        int port = getConfig().getInt("storage.port", defaultPort(backend));
        String database = getConfig().getString("storage.database", "provouchers");
        String username = getConfig().getString("storage.username", "provouchers");
        String password = getConfig().getString("storage.password", "");
        int poolSize = getConfig().getInt("storage.pool-size", 10);
        return switch (backend) {
            case MYSQL -> StorageConfig.mysql(host, port, database, username, password, poolSize,
                "provouchers-mysql");
            case MARIADB -> StorageConfig.mariadb(host, port, database, username, password, poolSize,
                "provouchers-mariadb");
            case POSTGRES -> StorageConfig.postgres(host, port, database, username, password, poolSize,
                "provouchers-postgres");
            default -> StorageConfig.sqlite(new File(getDataFolder(), "data.db").getAbsolutePath());
        };
    }

    private Backend parseBackend(String name) {
        try {
            return Backend.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getComponentLogger().warn(text("Unknown storage backend '" + name + "', falling back to "
                + "SQLite.", NamedTextColor.YELLOW));
            return Backend.SQLITE;
        }
    }

    private static int defaultPort(Backend backend) {
        return backend == Backend.POSTGRES ? 5432 : 3306;
    }

    /**
     * Writes a bundled resource to the data folder only when it is missing, since Bukkit's
     * {@code saveResource} logs a warning whenever the file already exists.
     */
    private void saveResourceIfMissing(String resourcePath) {
        if (!new File(getDataFolder(), resourcePath).exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void reportErrors(List<String> errors) {
        for (String error : errors) {
            getComponentLogger().warn(text("Voucher load error: " + error, NamedTextColor.YELLOW));
        }
    }

    private void disableWith(String reason) {
        getComponentLogger().error(text(reason + " Disabling.", NamedTextColor.RED));
        getServer().getPluginManager().disablePlugin(this);
    }
}
