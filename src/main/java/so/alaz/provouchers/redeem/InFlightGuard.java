package so.alaz.provouchers.redeem;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Admits one redemption at a time per player and voucher. An item redeem that verifies persistent
 * state (use limits, the anti-dupe stamp) off the player's thread holds a ticket from the click until
 * it is delivered or given back, so a second click in that window, even on a distinct stamped copy,
 * is refused instead of passing the cooldown and limit checks the first has not settled yet.
 *
 * <p>Thread-safe: a ticket is taken on the player's thread and may be released from any thread. A
 * ticket older than the lifetime no longer blocks, so a release that never happens can delay a
 * voucher but never lock it for good.
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
        // compute runs atomically per key, so of two simultaneous attempts exactly one installs its ticket.
        Ticket holder = held.compute(key,
            (k, current) -> current == null || current.expiresAt <= now ? fresh : current);
        return holder == fresh ? fresh : null;
    }

    /** Releases {@code ticket}. Idempotent, and a no-op once a newer ticket has replaced a lapsed one. */
    public void release(Ticket ticket) {
        held.remove(ticket.key, ticket);
    }
}
