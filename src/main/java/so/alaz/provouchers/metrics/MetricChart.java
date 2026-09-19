package so.alaz.provouchers.metrics;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A provider-agnostic custom metric. {@link MetricsBuilder} routes it to the matching native chart
 * on each enabled backend (bStats chart / FastStats metric), so one definition reports to both.
 */
public final class MetricChart {

    enum Type {STRING, NUMBER, BOOL, STRING_LIST, COUNTER}

    final String id;
    final Type type;
    @Nullable final Supplier<String> stringSupplier;
    @Nullable final Supplier<Integer> intSupplier;
    @Nullable final Supplier<Boolean> boolSupplier;
    @Nullable final Supplier<List<String>> listSupplier;
    @Nullable final LongSupplier counterSupplier;

    private MetricChart(String id, Type type, @Nullable Supplier<String> stringSupplier,
                        @Nullable Supplier<Integer> intSupplier, @Nullable Supplier<Boolean> boolSupplier,
                        @Nullable Supplier<List<String>> listSupplier, @Nullable LongSupplier counterSupplier) {
        this.id = id;
        this.type = type;
        this.stringSupplier = stringSupplier;
        this.intSupplier = intSupplier;
        this.boolSupplier = boolSupplier;
        this.listSupplier = listSupplier;
        this.counterSupplier = counterSupplier;
    }

    /** A single string value (bStats SimplePie / FastStats string metric). */
    public static MetricChart string(String id, Supplier<String> supplier) {
        return new MetricChart(id, Type.STRING, supplier, null, null, null, null);
    }

    /** A single integer value, reported as-is (bStats SingleLineChart / FastStats number metric). */
    public static MetricChart number(String id, Supplier<Integer> supplier) {
        return new MetricChart(id, Type.NUMBER, null, supplier, null, null, null);
    }

    /** A boolean value, reported as "true"/"false" (bStats SimplePie / FastStats bool metric). */
    public static MetricChart bool(String id, Supplier<Boolean> supplier) {
        return new MetricChart(id, Type.BOOL, null, null, supplier, null, null);
    }

    /** A list of strings (bStats AdvancedPie by frequency / FastStats string-array metric). */
    public static MetricChart stringList(String id, Supplier<List<String>> supplier) {
        return new MetricChart(id, Type.STRING_LIST, null, null, null, supplier, null);
    }

    /**
     * An event count backed by a monotonically increasing {@code total} (bStats SingleLineChart /
     * FastStats number metric). Each provider reports the increase since its own last report, so the
     * charts show activity per interval.
     */
    public static MetricChart counter(String id, LongSupplier total) {
        return new MetricChart(id, Type.COUNTER, null, null, null, null, total);
    }
}
