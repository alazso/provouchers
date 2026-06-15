package so.alaz.provouchers.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia-safe scheduling over Paper's region-threaded schedulers, behaving identically on
 * Paper and Folia. Bound to a single owning plugin so its tasks are cancelled when that
 * plugin disables. This is the single sanctioned scheduling entry point - never call
 * {@code Bukkit.getScheduler()} directly.
 */
public final class Scheduler {

    private final Plugin plugin;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs on the global region thread, as soon as possible. */
    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    /** Runs on the region thread currently owning the entity. */
    public void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    /**
     * Runs on the region thread owning the entity; if the entity has retired (e.g. the player logged
     * out) before the task runs, {@code retired} runs instead so a side effect is never simply dropped.
     */
    public void entity(Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), retired);
    }

    /** Runs on a dedicated async thread, as soon as possible. */
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    /** Runs {@code task} on an async thread after {@code delay}, then every {@code period} thereafter. */
    public void repeatingAsync(Runnable task, long delay, long period, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delay, period, unit);
    }
}
