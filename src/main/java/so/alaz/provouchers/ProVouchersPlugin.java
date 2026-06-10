package so.alaz.provouchers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import so.alaz.provouchers.antidupe.DupeDetector;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.api.VoucherService;
import so.alaz.provouchers.command.VoucherCommand;
import so.alaz.provouchers.condition.ConditionRegistry;
import so.alaz.provouchers.config.ConfigManager;
import so.alaz.provouchers.service.VoucherServiceImpl;
import so.alaz.provouchers.cooldown.CooldownService;
import so.alaz.provouchers.give.VoucherGiveService;
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
import so.alaz.provouchers.gui.PreviewGui;
import so.alaz.provouchers.gui.VoucherAdminMenu;
import so.alaz.provouchers.listener.CooldownLoadListener;
import so.alaz.provouchers.listener.VoucherInteractListener;
import so.alaz.provouchers.metrics.MetricCounters;
import so.alaz.provouchers.metrics.Metrics;
import so.alaz.provouchers.metrics.VoucherMetrics;
import so.alaz.provouchers.platform.CooldownManager;
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
import so.alaz.strata.api.StrataApi;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.text;

/**
 * Plugin entry point. Verifies the runtime prerequisites (Java 25 and the Strata
 * shared library), opens storage, loads voucher and code definitions, and wires up
 * the redeem pipeline, listener, and command.
 */
public final class ProVouchersPlugin extends JavaPlugin {

    private static final int MINIMUM_JAVA_FEATURE = 25;

    private VoucherStorage storage;
    private Metrics metrics;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        if (Runtime.version().feature() < MINIMUM_JAVA_FEATURE) {
            disableWith("ProVouchers requires Java " + MINIMUM_JAVA_FEATURE + " or newer, but the server "
                + "is running Java " + Runtime.version().feature() + ".");
            return;
        }
        if (!StrataApi.isAvailable()) {
            disableWith("Strata is not available. Install the Strata plugin and ensure it loads before "
                + "ProVouchers.");
            return;
        }

        saveDefaultConfig();
        saveExample("vouchers/example.yml");
        saveExample("codes/example.yml");

        Backend backend = parseBackend(getConfig().getString("storage.backend", "sqlite"));
        storage = new VoucherStorage(new StorageProvider(buildStorageConfig(backend)));
        storage.init().whenComplete((ignored, error) -> {
            if (error != null) {
                getComponentLogger().error(text("Failed to open ProVouchers storage; duplicate and code "
                    + "tracking will be unavailable until this is fixed.", NamedTextColor.RED), error);
            }
        });

        VoucherRegistry registry = new VoucherRegistry();
        ConfigManager configManager = new ConfigManager(getDataFolder(), registry);
        reportErrors(configManager.reload());

        Scheduler scheduler = new Scheduler(this);
        Text text = new Text();
        guiManager = new GuiManager(this);
        HookRegistry hooks = buildHooks();

        VoucherStamp stamp = new VoucherStamp(this);
        ItemResolver itemResolver = new ItemResolver(hooks);
        RewardExecutor rewardExecutor = new RewardExecutor(
            scheduler, text, itemResolver, hooks,
            getComponentLogger());
        VoucherItemFactory factory = new VoucherItemFactory(text, stamp, itemResolver);
        VoucherGiveService giveService = new VoucherGiveService(factory, scheduler);
        PreviewGui previewGui = new PreviewGui(registry, factory, giveService,
            new VoucherAdminMenu(text), guiManager, scheduler,
            text);

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
            cooldowns,
            new ConditionRegistry(text, hooks),
            counters,
            getConfig().getBoolean("anti-dupe.remove-on-discovery", true),
            getConfig().getBoolean("anti-dupe.warning.enabled", false),
            getConfig().getString("anti-dupe.warning.text", "<red>This item has been duplicated")
        );

        getServer().getPluginManager().registerEvents(
            new VoucherInteractListener(stamp, redeemHandler), this);
        getServer().getPluginManager().registerEvents(new CooldownLoadListener(cooldowns), this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);
        getServer().getOnlinePlayers().forEach(player -> cooldowns.hydrate(player.getUniqueId()));
        new VoucherCommand(registry, giveService, redeemHandler, configManager, previewGui, text).register(this);

        getServer().getServicesManager().register(VoucherService.class,
            new VoucherServiceImpl(registry, giveService), this,
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

    private void saveExample(String resourcePath) {
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
