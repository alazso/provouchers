package so.alaz.provouchers.antidupe;

import so.alaz.provouchers.storage.VoucherStorage;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Validates voucher item stamps against persistent storage. A stamp seen before
 * is a {@link StampStatus#DUPLICATE}; a storage error yields
 * {@link StampStatus#UNKNOWN} so callers can decide how strict to be.
 *
 * <p>{@link #check(String, String)} performs a blocking query and must run off the
 * main and region threads.
 */
public final class DupeDetector {

    private final VoucherStorage storage;

    public DupeDetector(VoucherStorage storage) {
        this.storage = storage;
    }

    /** Checks whether a stamp is unused, already redeemed, or unverifiable. */
    public StampStatus check(String batchId, String nonce) {
        try {
            return storage.isStampRedeemed(batchId, nonce) ? StampStatus.DUPLICATE : StampStatus.VALID;
        } catch (SQLException ex) {
            return StampStatus.UNKNOWN;
        }
    }

    /**
     * Atomically records a stamp as redeemed. Returns {@code true} if this call won
     * the race (the stamp was newly recorded), {@code false} if it was already taken.
     */
    public boolean claim(String batchId, String nonce, UUID player) {
        try {
            return storage.recordStamp(batchId, nonce, player);
        } catch (SQLException ex) {
            return false;
        }
    }
}
