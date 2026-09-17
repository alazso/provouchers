package so.alaz.provouchers.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * One provider's reporting position on a monotonic counter. Each provider holds its own mark, so a
 * single running total feeds several providers and each reports only the increase since its own last
 * report: nothing is split between providers or counted twice.
 */
final class CounterMark {

    private final LongSupplier total;
    private final AtomicLong reported = new AtomicLong();
    /** The total this thread last read with {@link #peekDelta()}, awaiting {@link #commit()}. */
    private final ThreadLocal<Long> pending = new ThreadLocal<>();

    CounterMark(LongSupplier total) {
        this.total = total;
    }

    /** The increase since the previous call, advancing the mark now. For a provider with no success callback. */
    long takeDelta() {
        long value = total.getAsLong();
        return Math.max(0L, value - reported.getAndSet(value));
    }

    /**
     * The increase since the last commit, without advancing the mark. A read that is never committed
     * (a failed submission, or an error report assembling metric data) leaves the count for the next report.
     */
    long peekDelta() {
        long value = total.getAsLong();
        pending.set(value);
        return Math.max(0L, value - reported.get());
    }

    /**
     * Advances the mark to the total this thread last read with {@link #peekDelta()}. Call it on the
     * submitting thread once a submission is accepted; FastStats runs {@code onFlush} there, and a read
     * made on another thread in the meantime cannot move this thread's mark.
     */
    void commit() {
        Long value = pending.get();
        if (value != null) {
            pending.remove();
            reported.accumulateAndGet(value, Math::max);
        }
    }
}
