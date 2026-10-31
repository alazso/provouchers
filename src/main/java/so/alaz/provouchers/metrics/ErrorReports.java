package so.alaz.provouchers.metrics;

import org.jetbrains.annotations.Nullable;

/**
 * Where the plugin reports an error it caught and handled.
 *
 * <p>The metrics provider captures only an error that escapes to the server or kills a thread, so
 * anything handled inside the plugin has to be reported here to be seen. Static because the classes
 * that handle these errors (storage, hooks, the anti-dupe check) hold no plugin reference, and
 * inert until {@link #install} runs, so a server with metrics disabled behaves exactly as before.
 */
public final class ErrorReports {

    @Nullable private static volatile Metrics metrics;

    private ErrorReports() {
    }

    /** Routes later reports to {@code handle}, or nowhere when it is {@code null}. */
    public static void install(@Nullable Metrics handle) {
        metrics = handle;
    }

    /** Stops reporting. Called on disable, before the provider shuts down. */
    public static void clear() {
        metrics = null;
    }

    /**
     * Reports {@code error}, identified by a short stable {@code source} key such as
     * {@code antidupe.claim}. Never throws, and does nothing when no provider is running.
     */
    public static void report(String source, Throwable error) {
        Metrics handle = metrics;
        if (handle != null) {
            handle.report(source, error);
        }
    }
}
