package so.alaz.provouchers.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia-safe scheduling over Paper's region-threaded schedulers, behaving identically on
 * Paper and Folia. Bound to a single owning plugin so its tasks are cancelled when that
 * plugin disables. This is the single sanctioned scheduling entry point - never call
 * {@code Bukkit.getScheduler()} directly.
 *
 * <p>A task Paper has not run by the time the owning plugin disables may never run: an entity
 * task then runs neither its task nor its retired callback, and a delayed task is cancelled.
 * Work that must settle is therefore also tracked by its caller in {@link PendingSettlements}
 * and settled while the plugin disables.
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
     * is gone or the plugin is disabling, so use {@link #entity(Entity, Runnable, Runnable)} when state
     * was already consumed.
     */
    public void entity(Entity entity, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            entity.getScheduler().run(plugin, scheduled -> task.run(), null);
        } catch (IllegalPluginAccessException ex) {
            // The plugin began disabling after the check: drop the task, as when the entity is gone.
        }
    }

    /**
     * Runs on the region thread owning the entity, or runs {@code retired} instead when the task cannot,
     * so a side effect is not simply dropped. Paper invokes {@code retired} if the entity retires (e.g.
     * the player logged out) before the task runs, but invokes neither when the entity was already gone
     * at scheduling time, or when the plugin is disabling and can no longer schedule; then
     * {@code retired} runs here, on the calling thread, so it must not assume a region thread.
     *
     * <p>At most one of the two runs, but not always one: a task already scheduled when the owning
     * plugin disables runs neither, since Paper never runs the task and skips {@code retired} once the
     * plugin is disabled. Work that must settle is therefore also tracked by its caller in
     * {@link PendingSettlements} and settled on disable.
     */
    public void entity(Entity entity, Runnable task, Runnable retired) {
        if (plugin.isEnabled()) {
            try {
                if (entity.getScheduler().run(plugin, scheduled -> task.run(), retired) != null) {
                    return;
                }
            } catch (IllegalPluginAccessException ex) {
                // The plugin began disabling after the check, so nothing was scheduled.
            }
        }
        retired.run();
    }

    /** Runs on a dedicated async thread, as soon as possible. */
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    /**
     * Runs on a dedicated async thread, as soon as possible, or runs {@code task} here, on the calling
     * thread, when the plugin can no longer schedule because it is disabling. For give-backs that must
     * not be dropped while the plugin disables. The task may block the calling thread then, so never
     * pass one that is unsafe to run there. A task scheduled before the plugin disables keeps running
     * once started, but one that has not started when Paper cancels the plugin's tasks, after
     * {@code onDisable} returns, never runs.
     */
    public void asyncOrRun(Runnable task) {
        if (plugin.isEnabled()) {
            try {
                Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
                return;
            } catch (IllegalPluginAccessException ex) {
                // The plugin began disabling after the check, so nothing was scheduled.
            }
        }
        task.run();
    }

    /**
     * Runs {@code task} once on an async thread after {@code delay}. Paper cancels it when the plugin
     * disables, so a task not yet due by then never runs, and scheduling throws while the plugin disables.
     */
    public void delayedAsync(Runnable task, long delay, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), delay, unit);
    }

    /** Runs {@code task} on an async thread after {@code delay}, then every {@code period} thereafter. */
    public void repeatingAsync(Runnable task, long delay, long period, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delay, period, unit);
    }
}
