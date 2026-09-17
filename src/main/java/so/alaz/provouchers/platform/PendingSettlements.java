package so.alaz.provouchers.platform;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Work handed to scheduler callbacks that must settle even when no callback runs. A task still scheduled
 * on an entity when the owning plugin disables runs neither its task nor its retired callback, and a
 * delayed task is cancelled, so the caller tracks such work here before scheduling it, and the plugin
 * settles whatever is still pending while it disables.
 *
 * <p>Each {@link Entry} is started exactly once: by the callback that runs first, which then does its own
 * side of the work (a delivery, a give-back), or by {@link #settleAll}, which runs the settlement the entry
 * was tracked with.
 *
 * <p>Thread-safe: entries are tracked and started from any thread, and compared by identity.
 */
public final class PendingSettlements {

    /** One piece of tracked work, pending until something starts it. */
    public final class Entry {

        private final AtomicBoolean started = new AtomicBoolean();
        private final Runnable settlement;

        private Entry(Runnable settlement) {
            this.settlement = settlement;
        }

        /**
         * Starts this entry and stops tracking it. Returns {@code true} for exactly one caller, which then
         * runs its side of the work, and {@code false} for every other.
         */
        public boolean start() {
            if (!started.compareAndSet(false, true)) {
                return false;
            }
            pending.remove(this);
            return true;
        }
    }

    private final Set<Entry> pending = ConcurrentHashMap.newKeySet();

    /**
     * Tracks work until something starts it. {@code settlement} runs only if {@link #settleAll} starts the
     * entry first. Track before scheduling the callbacks, so the work is pending before any can start it.
     */
    public Entry track(Runnable settlement) {
        Entry entry = new Entry(settlement);
        pending.add(entry);
        return entry;
    }

    /**
     * Starts every pending entry and runs its settlement on the calling thread, returning how many ran. An
     * entry a callback starts meanwhile is left to that callback, and a settlement that throws is passed to
     * {@code onFailure}, so the rest still run.
     */
    public int settleAll(Consumer<RuntimeException> onFailure) {
        int settled = 0;
        for (Entry entry : pending) {
            if (!entry.start()) {
                continue;
            }
            settled++;
            try {
                entry.settlement.run();
            } catch (RuntimeException ex) {
                onFailure.accept(ex);
            }
        }
        return settled;
    }
}
