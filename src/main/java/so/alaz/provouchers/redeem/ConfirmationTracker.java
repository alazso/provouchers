package so.alaz.provouchers.redeem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tracks pending two-step confirmations. A voucher flagged {@code two-step-authentication}
 * must be clicked a second time, within a window, to redeem: the first click records a
 * pending entry and asks for confirmation; a second click on the same voucher within the
 * window clears it and proceeds. A different voucher, or a click after the window lapsed,
 * starts a fresh confirmation.
 */
public final class ConfirmationTracker {

    private record Pending(String voucherId, long expiresAt) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final long windowMillis;
    private final LongSupplier clock;

    public ConfirmationTracker(long windowSeconds) {
        this(windowSeconds, System::currentTimeMillis);
    }

    ConfirmationTracker(long windowSeconds, LongSupplier clock) {
        this.windowMillis = Math.max(1, windowSeconds) * 1000L;
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
        pending.put(player, new Pending(voucherId, now + windowMillis));
        return true;
    }

    /** The confirmation window in seconds, for use in the confirmation message. */
    public long windowSeconds() {
        return windowMillis / 1000L;
    }
}
