package so.alaz.provouchers.redeem;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Admits one redemption at a time per player and voucher. An item redeem holds a ticket from the click
 * until it is delivered, or until its first attempt to give back what it held has finished; a second
 * click in that window, even on a distinct stamped copy, is refused.
 *
 * <p>Thread-safe: a ticket is taken on the player's thread and may be released from any thread. A
 * ticket stops blocking once its lifetime has passed, even if it is never released.
 */
public final class InFlightGuard {

    /**
     * Proof of holding the guard for one player and voucher. Compared by identity, so releasing a
     * lapsed ticket cannot free a newer holder's entry.
     */
    public static final class Ticket {

        private final Key key;
        private final long expiresAt;

        private Ticket(Key key, long expiresAt) {
            this.key = key;
            this.expiresAt = expiresAt;
        }
    }

    private record Key(UUID player, String voucherId) {
    }

    private final Map<Key, Ticket> held = new ConcurrentHashMap<>();
    private final long lifetimeMillis;
    private final LongSupplier clock;

    public InFlightGuard(long lifetimeSeconds) {
        this(lifetimeSeconds, System::currentTimeMillis);
    }

    InFlightGuard(long lifetimeSeconds, LongSupplier clock) {
        this.lifetimeMillis = Math.max(1, lifetimeSeconds) * 1000L;
        this.clock = clock;
    }

    /**
     * Takes the guard for this player and voucher. Returns the ticket to release once the redemption
     * settles, or {@code null} while another live ticket holds it.
     */
    @Nullable
    public Ticket acquire(UUID player, String voucherId) {
        long now = clock.getAsLong();
        Key key = new Key(player, voucherId);
        Ticket fresh = new Ticket(key, now + lifetimeMillis);
        // Atomic per key: of simultaneous attempts, exactly one installs its ticket.
        Ticket holder = held.compute(key,
            (k, current) -> current == null || current.expiresAt <= now ? fresh : current);
        return holder == fresh ? fresh : null;
    }

    /** Releases {@code ticket}. Idempotent, and a no-op once a newer ticket has replaced a lapsed one. */
    public void release(Ticket ticket) {
        held.remove(ticket.key, ticket);
    }
}
