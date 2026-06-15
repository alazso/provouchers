package so.alaz.provouchers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import so.alaz.provouchers.antidupe.DupeDetector;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.api.VoucherService;
import so.alaz.provouchers.command.Diagnostics;
import so.alaz.provouchers.command.StashCommand;
import so.alaz.provouchers.command.VoucherCommand;
import so.alaz.provouchers.condition.ConditionRegistry;
import so.alaz.provouchers.config.ConfigManager;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static net.kyori.adventure.text.Component.text;

/**
 * Plugin entry point. Verifies the Java 25 runtime requirement, opens storage,
 * loads voucher and code definitions, and wires up the redeem pipeline,
 * listeners, GUI, metrics, and command.
 */
public final class ProVouchersPlugin extends JavaPlugin {

    private static final int MINIMUM_JAVA_FEATURE = 25;

    private VoucherStorage storage;
    private Metrics metrics;
    private GuiManager guiManager;
    private DiscordWebhook discordWebhook;

    @Override
    public void onEnable() {
        if (Runtime.version().feature() < MINIMUM_JAVA_FEATURE) {
            disableWith("ProVouchers requires Java " + MINIMUM_JAVA_FEATURE + " or newer, but the server "
                + "is running Java " + Runtime.version().feature() + ".");
            return;
        }
        saveDefaultConfig();
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
        VoucherGiveService giveService = new VoucherGiveService(factory, scheduler);
        PreviewGui previewGui = new PreviewGui(registry, factory, giveService,
            new VoucherAdminMenu(text), guiManager, scheduler,
            text);
        RewardPreviewGui rewardPreviewGui = new RewardPreviewGui(factory, guiManager, scheduler, text);

        CooldownService cooldowns = new CooldownService(
            new CooldownManager(), storage, scheduler);

        MetricCounters counters = new MetricCounters();

        RedeemHandler redeemHandler = new RedeemHandler(
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
            loadCooldownTiers(),
            "gui".equalsIgnoreCase(getConfig().getString("redeem.confirm-style", "chat"))
                ? new ConfirmGui(factory, guiManager, scheduler, text) : null,
            getConfig().getBoolean("anti-dupe.remove-on-discovery", true),
            getConfig().getBoolean("anti-dupe.warning.enabled", false),
            getConfig().getString("anti-dupe.warning.text", "<red>This item has been duplicated"),
            getConfig().getBoolean("anti-dupe.notify.enabled", true),
            getConfig().getBoolean("redeem.batch-open-quiet", true),
            getConfig().getLong("redeem.confirm-window-seconds", 5L)
        );

        Duration stashExpiry = Durations.parseOrNull(getConfig().getString("stash.expire-after", ""));
        StashService stashService = new StashService(storage, registry, redeemHandler, scheduler, text,
            messages, getLogger(), stashExpiry != null ? stashExpiry.toMillis() : 0L);
        boolean stashEnabled = getConfig().getBoolean("stash.enabled", true);
        if (stashEnabled) {
            StashGui stashGui = new StashGui(stashService, registry, factory, guiManager, scheduler, text,
                messages, getConfig().getString("stash.title", "<gradient:#FFD700:#FF8A00>Your Stash"),
                getConfig().getInt("stash.rows", 6));
            new StashCommand(stashGui, text, messages)
                .register(this, getConfig().getStringList("stash.command-aliases"));
            if (getConfig().getBoolean("stash.notify-on-join", true)) {
                getServer().getPluginManager().registerEvents(
                    new StashJoinListener(stashService, scheduler, text, messages), this);
            }
            // Sweep lapsed entries periodically so an expiring Stash does not accrue dead rows.
            scheduler.repeatingAsync(stashService::pruneExpired, 60, 600, TimeUnit.SECONDS);
            // Overflow policy is read live, so /voucher reload can switch it without a restart.
            giveService.overflowPolicy(stashService,
                () -> "stash".equalsIgnoreCase(getConfig().getString("stash.overflow", "drop")));
        }

        getServer().getPluginManager().registerEvents(
            new VoucherInteractListener(stamp, redeemHandler, registry, rewardPreviewGui,
                getConfig().getBoolean("redeem.left-click-preview", true)), this);
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
        Diagnostics diagnostics = new Diagnostics(this, storage, registry, hooks, messages,
            () -> backend.name().toLowerCase(Locale.ROOT));
        FromhandGui fromhandGui = new FromhandGui(guiManager, configManager, messages, text, getDataFolder());
        MigrationService migrationService = new MigrationService(
            new CrazyVouchersMigrator(getDataFolder(), registry));
        new VoucherCommand(registry, giveService, redeemHandler, configManager, previewGui, text,
            messages, diagnostics, storage, scheduler, fromhandGui, migrationService, stashService)
            .register(this);

        getServer().getServicesManager().register(VoucherService.class,
            new VoucherServiceImpl(registry, giveService, stashService), this,
            ServicePriority.Normal);

        metrics = VoucherMetrics.start(this, registry, counters,
            () -> backend.name().toLowerCase(Locale.ROOT));

        getComponentLogger().info(text("ProVouchers enabled with " + registry.voucherCount()
            + " voucher(s) and " + registry.codeCount() + " code(s).", NamedTextColor.GOLD));
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeAll();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        if (storage != null) {
            storage.shutdown();
        }
        if (discordWebhook != null) {
            discordWebhook.close();
        }
        getComponentLogger().info(text("ProVouchers disabled.", NamedTextColor.GOLD));
    }

    /**
     * Registers every integration provider. Hooks self-detect their backing plugin and report
     * availability per call, so registering them unconditionally is safe; a missing plugin is
     * simply skipped at resolution time.
     */
    private static HookRegistry buildHooks() {
        HookRegistry hooks = new HookRegistry();
        hooks.register(EconomyHook.class, new VaultEconomyHook());
        hooks.register(PermissionHook.class, new LuckPermsPermissionHook());
        hooks.register(RegionHook.class, new WorldGuardRegionHook());
        hooks.register(ItemHook.class, new ItemsAdderItemHook());
        hooks.register(ItemHook.class, new OraxenItemHook());
        hooks.register(ItemHook.class, new NexoItemHook());
        hooks.register(ItemHook.class, new HeadDatabaseItemHook());
        return hooks;
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
     * Writes a bundled resource to the data folder only when it is missing. Avoids Bukkit's
     * {@code saveResource} warning that fires on every start once the file exists, which for a
     * non-overwriting save is the expected case, not a failure.
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
