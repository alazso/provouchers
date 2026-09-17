package so.alaz.provouchers.antidupe;

import so.alaz.provouchers.storage.VoucherStorage;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Validates voucher item stamps against persistent storage. A stamp seen before
 * is a {@link StampStatus#DUPLICATE}; a storage error yields
 * {@link StampStatus#UNKNOWN} so callers can decide how strict to be.
 *
 * <p>A claim is identified by the unique id, the claiming player, and the epoch
 * millis it was claimed at, so only the redemption that made a claim can give it
 * back, and a repeated give-back never removes a later claim of the same id.
 *
 * <p>{@link #check(String)}, {@link #claim(String, UUID, long)} and
 * {@link #release(String, UUID, long)} perform blocking queries and must run off
 * the main and region threads.
 */
public final class DupeDetector {

    private final VoucherStorage storage;

    public DupeDetector(VoucherStorage storage) {
        this.storage = storage;
    }

    /** Checks whether a unique id is unused, already redeemed, or unverifiable. */
    public StampStatus check(String uid) {
        try {
            return storage.isUsed(uid) ? StampStatus.DUPLICATE : StampStatus.VALID;
        } catch (SQLException | RuntimeException ex) {
            // Any storage failure, including the pool not being open yet, is unverifiable.
            return StampStatus.UNKNOWN;
        }
    }

    /**
     * Atomically records a unique id as redeemed by {@code player} at {@code claimedAt}
     * (epoch millis). Returns {@link ClaimOutcome#CLAIMED} if this claim is stored,
     * {@link ClaimOutcome#TAKEN} if the id was already claimed otherwise, and
     * {@link ClaimOutcome#UNKNOWN} on a storage failure, when this claim may or may not
     * have been stored.
     */
    public ClaimOutcome claim(String uid, UUID player, long claimedAt) {
        try {
            return storage.recordUse(uid, player, claimedAt) ? ClaimOutcome.CLAIMED : ClaimOutcome.TAKEN;
        } catch (SQLException | RuntimeException ex) {
            return ClaimOutcome.UNKNOWN;
        }
    }

    /**
     * Gives back the claim {@code player} made of a unique id at {@code claimedAt}, so the
     * item it was made for stays redeemable when its redemption is abandoned before
     * delivery. Returns {@code true} when no such claim remains (it was removed, or was
     * never stored), and {@code false} on a storage failure, which may leave it in place.
     */
    public boolean release(String uid, UUID player, long claimedAt) {
        try {
            storage.deleteUse(uid, player, claimedAt);
            return true;
        } catch (SQLException | RuntimeException ex) {
            return false;
        }
    }
}
