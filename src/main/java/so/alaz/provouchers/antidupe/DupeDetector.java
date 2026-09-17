package so.alaz.provouchers.antidupe;

import so.alaz.provouchers.storage.VoucherStorage;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Validates voucher item stamps against persistent storage. A stamp seen before
 * is a {@link StampStatus#DUPLICATE}; a storage error yields
 * {@link StampStatus#UNKNOWN} so callers can decide how strict to be.
 *
 * <p>{@link #check(String)}, {@link #claim(String, UUID)} and
 * {@link #release(String, UUID)} perform blocking queries and must run off the
 * main and region threads.
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
     * Atomically records a unique id as redeemed. Returns {@code true} if this call
     * won the race (newly recorded), {@code false} if it was already taken.
     */
    public boolean claim(String uid, UUID player) {
        try {
            return storage.recordUse(uid, player);
        } catch (SQLException | RuntimeException ex) {
            return false;
        }
    }

    /**
     * Undoes a successful {@link #claim} by {@code player}, so the item it was made for stays
     * redeemable when its redemption is abandoned before delivery. Returns {@code false} if nothing
     * was released, including on a storage failure, which leaves the id claimed.
     */
    public boolean release(String uid, UUID player) {
        try {
            return storage.deleteUse(uid, player);
        } catch (SQLException | RuntimeException ex) {
            return false;
        }
    }
}
