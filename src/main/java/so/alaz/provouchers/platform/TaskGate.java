package so.alaz.provouchers.platform;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admits async tasks until the owning plugin disables, and lets the disabling thread wait, for a bounded
 * time, for the tasks already admitted. A task enters before its first storage call and exits when done.
 * Once {@link #close} begins every later entry is refused, so a task that starts too late does nothing
 * that would need settling, and once the admitted tasks have exited, all they left to settle is tracked.
 *
 * <p>Thread-safe: tasks enter and exit on any thread.
 */
public final class TaskGate {

    /** How long {@link #close} sleeps between checks for admitted tasks still running. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private final AtomicInteger running = new AtomicInteger();
    private volatile boolean closing;

    /**
     * Admits a task, or returns {@code false} once {@link #close} has begun, when the task must do nothing.
     * An admitted task must call {@link #exit} when done, in a finally.
     */
    public boolean enter() {
        // Counted before the flag is read, so a concurrent close either waits for this task or refuses it.
        running.incrementAndGet();
        if (closing) {
            running.decrementAndGet();
            return false;
        }
        return true;
    }

    /** Marks an admitted task done. */
    public void exit() {
        running.decrementAndGet();
    }

    /**
     * Refuses every later entry, then waits up to {@code wait} for the admitted tasks to exit. Returns how
     * many are still running: {@code 0} once all have exited. An interrupt ends the wait early and stays set.
     */
    public int close(Duration wait) {
        closing = true;
        long deadline = System.nanoTime() + Math.max(0, wait.toNanos());
        int remaining;
        while ((remaining = running.get()) > 0) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return remaining;
            }
            try {
                Thread.sleep(Duration.ofNanos(Math.min(left, POLL_INTERVAL.toNanos())));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return running.get();
            }
        }
        return 0;
    }
}
