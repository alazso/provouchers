package so.alaz.provouchers.metrics;

import dev.faststats.SdkInfo;
import dev.faststats.bukkit.BukkitContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A live metrics submission, returned by {@link MetricsBuilder#start()}. Wraps the started bStats
 * metrics and/or FastStats context. {@link #shutdown()} must be called on plugin disable.
 *
 * <p>The state methods report what each provider is actually doing, for {@code /voucher doctor}.
 * They read cached state without blocking, so they are safe on any thread.
 */
public final class Metrics {

    @Nullable private final org.bstats.bukkit.Metrics bStats;
    @Nullable private final BukkitContext fastStats;

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
     * {@code config/faststats/config.properties} or with {@code -Dfaststats.enabled=false}.
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

    /** Stops submission for all enabled providers. Idempotent. */
    public void shutdown() {
        if (bStats != null) {
            try {
                bStats.shutdown();
            } catch (RuntimeException ignored) {
                // Shutting down metrics must never throw out of disable.
            }
        }
        if (fastStats != null) {
            try {
                fastStats.shutdown();
            } catch (RuntimeException ignored) {
            }
        }
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
