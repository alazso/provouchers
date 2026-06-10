package so.alaz.provouchers.metrics;

import dev.faststats.bukkit.BukkitContext;
import org.jetbrains.annotations.Nullable;

/**
 * A live metrics submission, returned by {@link MetricsBuilder#start()}. Wraps the started bStats
 * metrics and/or FastStats context. Shut it down on plugin disable.
 */
public final class Metrics {

    @Nullable private final org.bstats.bukkit.Metrics bStats;
    @Nullable private final BukkitContext fastStats;

    Metrics(@Nullable org.bstats.bukkit.Metrics bStats, @Nullable BukkitContext fastStats) {
        this.bStats = bStats;
        this.fastStats = fastStats;
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
                // As above.
            }
        }
    }
}
