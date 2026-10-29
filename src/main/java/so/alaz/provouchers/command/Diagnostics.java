package so.alaz.provouchers.command;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.hook.EconomyHook;
import so.alaz.provouchers.hook.Hook;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.hook.ItemHook;
import so.alaz.provouchers.hook.PermissionHook;
import so.alaz.provouchers.hook.RegionHook;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.metrics.Metrics;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Gathers the health report for {@code /voucher doctor}: version and server flavor, the
 * storage backend and pool state, loaded content counts, detected integrations, locale setup,
 * and whether metrics are on. Read-only and non-blocking, so it may run on the command thread.
 */
public final class Diagnostics {

    private final JavaPlugin plugin;
    private final VoucherStorage storage;
    private final VoucherRegistry registry;
    private final HookRegistry hooks;
    private final Messages messages;
    private final Supplier<String> backend;
    private final Supplier<Metrics> metrics;
    private final Supplier<String> comfiConf;
    private final boolean metricsEnabledAtStartup;

    public Diagnostics(JavaPlugin plugin, VoucherStorage storage, VoucherRegistry registry,
                       HookRegistry hooks, Messages messages, Supplier<String> backend,
                       Supplier<Metrics> metrics, Supplier<String> comfiConf,
                       boolean metricsEnabledAtStartup) {
        this.plugin = plugin;
        this.storage = storage;
        this.registry = registry;
        this.hooks = hooks;
        this.messages = messages;
        this.backend = backend;
        this.metrics = metrics;
        this.comfiConf = comfiConf;
        this.metricsEnabledAtStartup = metricsEnabledAtStartup;
    }

    /** The report lines, as MiniMessage strings. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Version: <white>" + plugin.getPluginMeta().getVersion()
            + " <dark_gray>on " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        lines.add("<gray>Java: <white>" + Runtime.version());
        lines.add("<gray>Storage: <white>" + backend.get()
            + (storage.isReady() ? " <green>(connected)" : " <red>(unavailable)"));
        lines.add("<gray>Content: <white>" + registry.voucherCount() + " vouchers<gray>, <white>"
            + registry.codeCount() + " codes");
        lines.add("<gray>Economy: " + providerName(hooks.get(EconomyHook.class)));
        lines.add("<gray>Permissions: " + providerName(hooks.get(PermissionHook.class)));
        lines.add("<gray>Items: " + itemProviders());
        lines.add("<gray>Regions: " + providerName(hooks.get(RegionHook.class)));
        lines.add("<gray>Locale: <white>" + messages.defaultLocale()
            + (messages.perPlayer() ? " <dark_gray>(per-player)" : "")
            + " <gray>| langs: <white>" + languages() + localePending());
        lines.add("<gray>Metrics: " + metricsState());
        lines.add("<gray>ComfiConf: " + comfiConf.get());
        return lines;
    }

    /**
     * What each metrics provider is doing: whether it started, and for FastStats its SDK version,
     * whether it submits metrics, and whether error reports are submitted. The state comes from the providers
     * themselves, not from the saved {@code metrics.enabled}: that switch is read once at startup, so a change to
     * it is still waiting for a restart and would otherwise be reported as what the server is doing.
     */
    private String metricsState() {
        Metrics handle = metrics.get();
        if (handle == null) {
            return (metricsEnabledAtStartup
                ? "<red>not started <dark_gray>(no provider configured, or enable failed)"
                : "<gray>off <dark_gray>(metrics.enabled: false)") + metricsPending();
        }
        return "<white>bStats " + (handle.bStatsStarted() ? "<green>on" : "<red>not started")
            + "<gray>, " + fastStatsState(handle) + metricsPending();
    }

    /** Says so when the saved {@code metrics.enabled} no longer matches the value the providers started with. */
    private String metricsPending() {
        return plugin.getConfig().getBoolean("metrics.enabled", true) == metricsEnabledAtStartup
            ? "" : " <yellow>(metrics.enabled changed; takes effect after a restart)";
    }

    /** Says so when the saved locale settings no longer match the ones the messages were loaded with. */
    private String localePending() {
        boolean same = messages.defaultLocale()
            .equals(Messages.normalizeLocale(plugin.getConfig().getString("locale.default")))
            && messages.perPlayer() == plugin.getConfig().getBoolean("locale.per-player", true);
        return same ? "" : " <yellow>(locale changed; takes effect after a restart)";
    }

    private static String fastStatsState(Metrics handle) {
        String sdk = handle.fastStatsSdk();
        if (sdk == null) {
            return "<white>FastStats <red>not started";
        }
        String state;
        if (!handle.fastStatsEnabled()) {
            state = "<yellow>opted out on this server";
        } else if (!handle.fastStatsSubmitting()) {
            state = "<yellow>not submitting metrics";
        } else {
            state = "<green>on";
        }
        return "<white>FastStats " + sdk + " " + state
            + " <dark_gray>(errors " + (handle.fastStatsErrorTracking() ? "on" : "off") + ")";
    }

    private String languages() {
        TreeSet<String> langs = new TreeSet<>(messages.loadedLanguages());
        return langs.isEmpty() ? "en only" : String.join(", ", langs);
    }

    private String itemProviders() {
        List<String> names = new ArrayList<>();
        for (ItemHook hook : hooks.all(ItemHook.class)) {
            if (hook.isAvailable()) {
                names.add(hook.name());
            }
        }
        return names.isEmpty() ? "<gray>none" : "<white>" + String.join(", ", names);
    }

    private static String providerName(@Nullable Hook hook) {
        return hook == null ? "<gray>none" : "<white>" + hook.name();
    }
}
