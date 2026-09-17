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

    /**
     * Runs on the region thread currently owning the entity. The task is silently dropped if the entity
     * is gone, so use {@link #entity(Entity, Runnable, Runnable)} when state was already consumed.
     */
    public void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    /**
     * Runs on the region thread owning the entity, or runs {@code retired} instead when the task cannot:
     * exactly one of the two runs, so a side effect is never simply dropped. Paper invokes
     * {@code retired} if the entity retires (e.g. the player logged out) before the task runs, but
     * invokes neither when the entity was already gone at scheduling time; then {@code retired} runs
     * here, on the calling thread, so it must not assume a region thread.
     */
    public void entity(Entity entity, Runnable task, Runnable retired) {
        if (entity.getScheduler().run(plugin, scheduled -> task.run(), retired) == null) {
            retired.run();
        }
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
