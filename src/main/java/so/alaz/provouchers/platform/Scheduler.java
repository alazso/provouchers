package so.alaz.provouchers.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia-safe scheduling over Paper's region-threaded schedulers, identical on Paper and Folia. Tasks
 * are bound to the owning plugin and cancelled when it disables. All plugin scheduling goes through
 * this class: {@code Bukkit.getScheduler()} is unsupported on Folia and is never called directly.
 *
 * <p>A task Paper has not run by the time the owning plugin disables may never run: an entity task
 * then runs neither its task nor its retired callback, and a delayed task is cancelled. Work that
 * must settle is tracked by its caller in {@link PendingSettlements} and settled while the plugin
 * disables.
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
     * Runs on the region thread owning the entity. The task is dropped if the entity is gone or the
     * plugin is disabling.
     */
    public void entity(Entity entity, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            entity.getScheduler().run(plugin, scheduled -> task.run(), null);
        } catch (IllegalPluginAccessException ex) {
            // The plugin began disabling after the check.
        }
    }

    /**
     * Runs {@code task} on the region thread owning the entity, or {@code retired} when the task cannot
     * run. Paper runs {@code retired} if the entity retires (e.g. the player logs out) before the task
     * runs. When the entity is already removed, Paper returns {@code null} and runs neither, and while
     * the plugin is disabling nothing can be scheduled; in both cases {@code retired} runs here, on the
     * calling thread, so it must not assume a region thread.
     *
     * <p>At most one of the two runs. A task still scheduled when the owning plugin disables runs
     * neither, so work that must settle is also tracked by its caller in {@link PendingSettlements}.
     */
    public void entity(Entity entity, Runnable task, Runnable retired) {
        if (plugin.isEnabled()) {
            try {
                if (entity.getScheduler().run(plugin, scheduled -> task.run(), retired) != null) {
                    return;
                }
            } catch (IllegalPluginAccessException ex) {
                // The plugin began disabling after the check.
            }
        }
        retired.run();
    }

    /** Runs on a dedicated async thread, as soon as possible. */
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    /**
     * Runs on a dedicated async thread, as soon as possible, or on the calling thread when the plugin
     * is disabling and cannot schedule. The task may then block the calling thread, so it must be safe
     * to run there. A task scheduled before the plugin disables keeps running once started, but
     * one that has not started when Paper cancels the plugin's tasks, after {@code onDisable} returns,
     * never runs.
     */
    public void asyncOrRun(Runnable task) {
        if (plugin.isEnabled()) {
            try {
                Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
                return;
            } catch (IllegalPluginAccessException ex) {
                // The plugin began disabling after the check.
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
