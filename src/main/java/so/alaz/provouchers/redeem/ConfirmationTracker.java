package so.alaz.provouchers.redeem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tracks pending two-step confirmations. A voucher flagged {@code two-step-authentication} redeems only
 * on a second click on the same voucher within the window.
 */
public final class ConfirmationTracker {

    private record Pending(String voucherId, long expiresAt) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** Read on every use, so a changed window applies without a restart. */
    private final LongSupplier windowSeconds;
    private final LongSupplier clock;

    public ConfirmationTracker(LongSupplier windowSeconds) {
        this(windowSeconds, System::currentTimeMillis);
    }

    ConfirmationTracker(LongSupplier windowSeconds, LongSupplier clock) {
        this.windowSeconds = windowSeconds;
        this.clock = clock;
    }

    /**
     * Whether this redeem still needs confirmation. Returns {@code true} and records a
     * pending confirmation on the first click (or after the window lapsed, or for a
     * different voucher); returns {@code false} and clears the entry when a live pending
     * confirmation for the same voucher is met by a second click.
     */
    public boolean needsConfirm(UUID player, String voucherId) {
        long now = clock.getAsLong();
        Pending current = pending.get(player);
        if (current != null && current.voucherId().equals(voucherId) && now <= current.expiresAt()) {
            pending.remove(player);
            return false;
        }
        pending.put(player, new Pending(voucherId, now + windowSeconds() * 1000L));
        return true;
    }

    public void clear(UUID player) {
        pending.remove(player);
    }

    public long windowSeconds() {
        return Math.max(1, windowSeconds.getAsLong());
    }
}
