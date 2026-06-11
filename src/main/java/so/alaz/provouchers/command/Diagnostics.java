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
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Gathers a quick health report for {@code /voucher doctor}: version and server flavor, the
 * storage backend and pool state, loaded content counts, detected integrations, locale setup,
 * and whether metrics are on. Read-only and non-blocking, so it is safe on the command thread.
 */
public final class Diagnostics {

    private final JavaPlugin plugin;
    private final VoucherStorage storage;
    private final VoucherRegistry registry;
    private final HookRegistry hooks;
    private final Messages messages;
    private final Supplier<String> backend;

    public Diagnostics(JavaPlugin plugin, VoucherStorage storage, VoucherRegistry registry,
                       HookRegistry hooks, Messages messages, Supplier<String> backend) {
        this.plugin = plugin;
        this.storage = storage;
        this.registry = registry;
        this.hooks = hooks;
        this.messages = messages;
        this.backend = backend;
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
        lines.add("<gray>Locale: <white>" + plugin.getConfig().getString("locale.default", "en")
            + (plugin.getConfig().getBoolean("locale.per-player", true) ? " <dark_gray>(per-player)" : "")
            + " <gray>| langs: <white>" + languages());
        lines.add("<gray>Metrics: " + (plugin.getConfig().getBoolean("metrics.enabled", true)
            ? "<green>on" : "<gray>off"));
        return lines;
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
