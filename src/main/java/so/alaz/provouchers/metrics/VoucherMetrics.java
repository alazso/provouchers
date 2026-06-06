package so.alaz.provouchers.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.strata.api.StrataApi;
import so.alaz.strata.api.metrics.MetricChart;
import so.alaz.strata.api.metrics.MetricProvider;
import so.alaz.strata.api.metrics.Metrics;
import so.alaz.strata.api.metrics.MetricsBuilder;

import java.util.function.Supplier;

/**
 * Wires ProVouchers usage metrics through Strata, which ships bStats and FastStats
 * shaded and relocated so the plugin bundles neither.
 *
 * <p>The bStats id and FastStats token below identify ProVouchers itself (one per
 * plugin, set by the author), not the server. A provider is only enabled once its
 * credential is filled in, and the whole feature respects {@code metrics.enabled}
 * in {@code config.yml}.
 */
public final class VoucherMetrics {

    /** ProVouchers' bStats service id. Set this once registered at https://bstats.org. */
    private static final int BSTATS_ID = 0;

    /** ProVouchers' FastStats project token. Set this once a FastStats project exists. */
    private static final String FASTSTATS_TOKEN = "";

    private VoucherMetrics() {
    }

    /**
     * Starts metrics submission, or returns {@code null} when metrics are disabled
     * in config or no provider credential is configured. The caller keeps the handle
     * and calls {@link Metrics#shutdown()} on plugin disable.
     */
    @Nullable
    public static Metrics start(JavaPlugin plugin, VoucherRegistry registry, Supplier<String> backend) {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) {
            return null;
        }
        MetricsBuilder builder = StrataApi.metrics().create(plugin);
        boolean anyProvider = false;
        if (BSTATS_ID > 0) {
            builder.enable(MetricProvider.BSTATS, Integer.toString(BSTATS_ID));
            anyProvider = true;
        }
        if (!FASTSTATS_TOKEN.isEmpty()) {
            builder.enable(MetricProvider.FASTSTATS, FASTSTATS_TOKEN);
            builder.errorTracking(true);
            anyProvider = true;
        }
        if (!anyProvider) {
            return null;
        }
        builder
            .addChart(MetricChart.string("storage_backend", backend))
            .addChart(MetricChart.number("loaded_vouchers", registry::voucherCount))
            .addChart(MetricChart.number("loaded_codes", registry::codeCount));
        return builder.start();
    }
}
