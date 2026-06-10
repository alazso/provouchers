package so.alaz.provouchers.antidupe;

import so.alaz.provouchers.storage.VoucherStorage;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Validates voucher item stamps against persistent storage. A stamp seen before
 * is a {@link StampStatus#DUPLICATE}; a storage error yields
 * {@link StampStatus#UNKNOWN} so callers can decide how strict to be.
 *
 * <p>{@link #check(String)} performs a blocking query and must run off the
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
}
