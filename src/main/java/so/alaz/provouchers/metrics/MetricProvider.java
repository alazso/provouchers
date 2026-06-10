package so.alaz.provouchers.metrics;

/** Supported metrics backends. A plugin may enable either or both. */
public enum MetricProvider {
    /** bStats, identified by an integer service id (passed as a string). */
    BSTATS,

    /** FastStats, identified by a project token. */
    FASTSTATS,
}
