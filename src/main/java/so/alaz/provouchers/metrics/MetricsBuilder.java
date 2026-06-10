package so.alaz.provouchers.metrics;

import dev.faststats.ErrorTracker;
import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.CustomChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates provider ids and charts, then on {@link #start()} initializes each enabled backend.
 * Backend init is wrapped so a failure (bad id, network, missing context) disables that provider
 * without taking down plugin enable. Charts are routed to every enabled provider.
 */
public final class MetricsBuilder {

    // FastStats default anonymizers: scrub common secrets from error traces before upload.
    private static final String[][] ANONYMIZERS = {
        {"[\\w.-]+@([\\w-]+\\.)+[\\w-]{2,4}", "[email hidden]"},
        {"Bearer [A-Za-z0-9._~+/=-]+", "Bearer [token hidden]"},
        {"AKIA[0-9A-Z]{16}", "[aws-key hidden]"},
        {"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "[uuid hidden]"},
        {"([?&](?:api_?key|token|secret)=)[^&\\s]+", "$1[redacted]"},
        {"jdbc:[^\\s\"']+", "jdbc:[redacted]"},
        {"(?i)\\b(SELECT|INSERT|UPDATE|DELETE)\\b[^\\n]{0,200}", "[sql hidden]"},
        {"[A-Za-z]:\\\\[^\\s\"']+", "[path hidden]"},
    };

    private final JavaPlugin plugin;
    private final Map<MetricProvider, String> ids = new EnumMap<>(MetricProvider.class);
    private final List<MetricChart> charts = new ArrayList<>();
    private boolean errorTracking;

    public MetricsBuilder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Enables a provider with its bStats service id or FastStats project token. */
    public MetricsBuilder enable(MetricProvider provider, String id) {
        ids.put(provider, id);
        return this;
    }

    /** Adds a custom chart, routed to every enabled provider. */
    public MetricsBuilder addChart(MetricChart chart) {
        charts.add(chart);
        return this;
    }

    /** Enables FastStats error tracking with the default anonymizers. No effect without FastStats. */
    public MetricsBuilder errorTracking(boolean enabled) {
        this.errorTracking = enabled;
        return this;
    }

    /** Starts submission for all enabled providers and returns the live handle. */
    public Metrics start() {
        org.bstats.bukkit.Metrics bStats = ids.containsKey(MetricProvider.BSTATS)
            ? startBStats(ids.get(MetricProvider.BSTATS)) : null;
        BukkitContext fastStats = ids.containsKey(MetricProvider.FASTSTATS)
            ? startFastStats(ids.get(MetricProvider.FASTSTATS)) : null;
        return new Metrics(bStats, fastStats);
    }

    @Nullable
    private org.bstats.bukkit.Metrics startBStats(String id) {
        try {
            org.bstats.bukkit.Metrics metrics = new org.bstats.bukkit.Metrics(plugin, Integer.parseInt(id));
            for (MetricChart chart : charts) {
                metrics.addCustomChart(toBStatsChart(chart));
            }
            return metrics;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("bStats metrics failed to start: " + ex.getMessage());
            return null;
        }
    }

    @Nullable
    private BukkitContext startFastStats(String token) {
        try {
            BukkitContext.Factory factory = new BukkitContext.Factory(plugin, token)
                .metrics(metricsFactory -> {
                    var built = metricsFactory;
                    for (MetricChart chart : charts) {
                        built = built.addMetric(toFastStatsMetric(chart));
                    }
                    return built.create();
                });
            if (errorTracking) {
                factory = factory.errorTrackerService(defaultErrorTracker());
            }
            BukkitContext context = factory.create();
            context.ready();
            return context;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("FastStats metrics failed to start: " + ex.getMessage());
            return null;
        }
    }

    private static CustomChart toBStatsChart(MetricChart chart) {
        return switch (chart.type) {
            case STRING -> new SimplePie(chart.id, () -> chart.stringSupplier.get());
            case NUMBER -> new SingleLineChart(chart.id, () -> chart.intSupplier.get());
            case BOOL -> new SimplePie(chart.id, () -> chart.boolSupplier.get().toString());
            case STRING_LIST -> new AdvancedPie(chart.id, () -> frequency(chart.listSupplier.get()));
        };
    }

    private static Metric<?> toFastStatsMetric(MetricChart chart) {
        return switch (chart.type) {
            case STRING -> Metric.string(chart.id, () -> chart.stringSupplier.get());
            case NUMBER -> Metric.number(chart.id, () -> chart.intSupplier.get());
            case BOOL -> Metric.bool(chart.id, () -> chart.boolSupplier.get());
            case STRING_LIST -> Metric.stringArray(chart.id, () -> chart.listSupplier.get().toArray(String[]::new));
        };
    }

    private static Map<String, Integer> frequency(List<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        for (String value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    private static ErrorTracker defaultErrorTracker() {
        ErrorTracker tracker = ErrorTracker.contextAware();
        for (String[] rule : ANONYMIZERS) {
            tracker = tracker.anonymize(rule[0], rule[1]);
        }
        return tracker;
    }
}
