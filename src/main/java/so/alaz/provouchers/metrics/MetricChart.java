package so.alaz.provouchers.metrics;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A provider-agnostic custom metric. Build one with the factory methods; the builder routes it to
 * the matching native chart on each enabled backend (bStats chart / FastStats metric), so the same
 * definition reports to both.
 */
public final class MetricChart {

    enum Type {STRING, NUMBER, BOOL, STRING_LIST}

    final String id;
    final Type type;
    @Nullable final Supplier<String> stringSupplier;
    @Nullable final Supplier<Integer> intSupplier;
    @Nullable final Supplier<Boolean> boolSupplier;
    @Nullable final Supplier<List<String>> listSupplier;

    private MetricChart(String id, Type type, @Nullable Supplier<String> stringSupplier,
                        @Nullable Supplier<Integer> intSupplier, @Nullable Supplier<Boolean> boolSupplier,
                        @Nullable Supplier<List<String>> listSupplier) {
        this.id = id;
        this.type = type;
        this.stringSupplier = stringSupplier;
        this.intSupplier = intSupplier;
        this.boolSupplier = boolSupplier;
        this.listSupplier = listSupplier;
    }

    /** A single string value (bStats SimplePie / FastStats string metric). */
    public static MetricChart string(String id, Supplier<String> supplier) {
        return new MetricChart(id, Type.STRING, supplier, null, null, null);
    }

    /** A single integer value (bStats SingleLineChart / FastStats number metric). */
    public static MetricChart number(String id, Supplier<Integer> supplier) {
        return new MetricChart(id, Type.NUMBER, null, supplier, null, null);
    }

    /** A boolean value, reported as "true"/"false" (bStats SimplePie / FastStats bool metric). */
    public static MetricChart bool(String id, Supplier<Boolean> supplier) {
        return new MetricChart(id, Type.BOOL, null, null, supplier, null);
    }

    /** A list of strings (bStats AdvancedPie by frequency / FastStats string-array metric). */
    public static MetricChart stringList(String id, Supplier<List<String>> supplier) {
        return new MetricChart(id, Type.STRING_LIST, null, null, null, supplier);
    }
}
