package so.alaz.provouchers.redeem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stamp ids this server has claimed, or is claiming or giving back, for an item redeem that has not
 * settled. A redeem enters its item's id before it touches storage and ends the entry once its claim is
 * delivered or given back, so while the entry stands no other redeem on this server checks the id (and
 * mistakes the pending claim for a duplicate) or claims it.
 *
 * <p>Thread-safe: entries are entered and ended from any thread. Each belongs to the owner that entered
 * it, compared by identity, so only that redeem can end it.
 */
public final class ClaimLedger {

    private final Map<String, Object> owners = new ConcurrentHashMap<>();

    /**
     * Enters {@code uid} for {@code owner}. Returns {@code false}, changing nothing, while any owner
     * (this one included) already holds it.
     */
    public boolean begin(String uid, Object owner) {
        return owners.putIfAbsent(uid, owner) == null;
    }

    /** Ends {@code owner}'s entry for {@code uid}. Idempotent, and a no-op while another owner holds it. */
    public void end(String uid, Object owner) {
        owners.computeIfPresent(uid, (key, current) -> current == owner ? null : current);
    }

    /** Whether any owner holds {@code uid}. */
    public boolean holds(String uid) {
        return owners.containsKey(uid);
    }
}
