package so.alaz.provouchers.metrics;

import dev.faststats.Attributes;
import dev.faststats.SdkInfo;
import dev.faststats.bukkit.BukkitContext;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * A live metrics submission, returned by {@link MetricsBuilder#start()}. Wraps the started bStats
 * metrics and/or FastStats context. {@link #shutdown(Duration)} must be called on plugin disable.
 *
 * <p>The state methods report what each provider is actually doing, for {@code /voucher doctor}.
 * They read cached state without blocking, so they are safe on any thread.
 */
public final class Metrics {

    @Nullable private final org.bstats.bukkit.Metrics bStats;
    @Nullable private final BukkitContext fastStats;
    private final AtomicBoolean stopped = new AtomicBoolean();

    Metrics(@Nullable org.bstats.bukkit.Metrics bStats, @Nullable BukkitContext fastStats) {
        this.bStats = bStats;
        this.fastStats = fastStats;
    }

    /** Whether bStats submission started. */
    public boolean bStatsStarted() {
        return bStats != null;
    }

    /** The FastStats SDK name and version, or {@code null} when its context did not start. */
    @Nullable
    public String fastStatsSdk() {
        if (fastStats == null) {
            return null;
        }
        try {
            SdkInfo info = fastStats.getSdkInfo();
            return info.getName() + " " + info.getVersion();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Whether FastStats is enabled for this server. It is off when the server owner opted out in
     * {@code plugins/faststats/config.properties} or with {@code -Dfaststats.enabled=false}.
     */
    public boolean fastStatsEnabled() {
        return reads(context -> context.getConfig().enabled());
    }

    /** Whether FastStats submits metrics: its metrics service was created and metrics are enabled. */
    public boolean fastStatsSubmitting() {
        return reads(context -> context.metrics().isPresent() && context.getConfig().submitMetrics());
    }

    /** Whether FastStats submits error reports: its error service was created and tracking is enabled. */
    public boolean fastStatsErrorTracking() {
        return reads(context -> context.errorTrackerService().isPresent()
            && context.getConfig().errorTracking());
    }

    /**
     * Reports an error the plugin caught and handled, so it reaches the provider's error tracking.
     * Automatic capture only sees an error that escapes to the server or kills a thread, so an error
     * handled here is invisible without this call. Does nothing without a provider, and never throws.
     *
     * @param source where the error was handled, as a short stable key
     */
    public void report(String source, Throwable error) {
        if (fastStats == null) {
            return;
        }
        try {
            fastStats.errorTrackerService().ifPresent(service -> service.globalErrorTracker()
                .trackError(error)
                .attributes(Attributes.empty().put("source", source))
                .handled(true));
        } catch (RuntimeException ignored) {
            // Reporting an error must never raise one.
        }
    }

    /**
     * Stops submission for all enabled providers. FastStats sends its last batch synchronously on the
     * thread that stops it, so that send runs on a daemon thread and this waits at most {@code limit}
     * for it. Only the first call stops anything; later calls return at once.
     *
     * @throws TimeoutException if the last FastStats batch is still sending after {@code limit}; the
     *     send continues on its daemon thread
     * @throws InterruptedException if interrupted while waiting for that send
     */
    public void shutdown(Duration limit) throws TimeoutException, InterruptedException {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (bStats != null) {
            try {
                bStats.shutdown();
            } catch (RuntimeException ignored) {
                // A bStats failure must not skip the FastStats shutdown below.
            }
        }
        BukkitContext context = fastStats;
        if (context == null) {
            return;
        }
        boolean finished = runBounded("provouchers-faststats-shutdown", () -> {
            try {
                context.shutdown();
            } catch (RuntimeException | LinkageError ignored) {
                // After a timed-out wait this runs past disable, where the unloaded plugin's classes can fail to link.
            }
        }, limit);
        if (!finished) {
            throw new TimeoutException("the final FastStats send did not finish within "
                + limit.toMillis() + " ms and continues in the background");
        }
    }

    /**
     * Runs {@code task} on a new daemon thread named {@code name} and waits at most {@code limit} for it.
     *
     * @return whether {@code task} finished within {@code limit}; when it did not, it keeps running
     */
    static boolean runBounded(String name, Runnable task, Duration limit) throws InterruptedException {
        Thread thread = Thread.ofPlatform().name(name).daemon().start(task);
        return thread.join(limit);
    }

    /** Reads one fact from the FastStats context, or {@code false} when it cannot be read. */
    private boolean reads(Predicate<BukkitContext> fact) {
        if (fastStats == null) {
            return false;
        }
        try {
            return fact.test(fastStats);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
