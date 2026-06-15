package so.alaz.provouchers.stash;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.storage.VoucherStorage;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The async gateway to the Stash: queues virtual (itemless) vouchers for players. Storage runs on the
 * async scheduler and a failure is logged rather than thrown back to the caller, so a give never fails
 * because the database hiccuped. The claim side arrives with the Stash GUI.
 */
public final class StashService {

    private final VoucherStorage storage;
    private final Scheduler scheduler;
    private final Logger logger;
    /** Default lifetime for new entries in millis, or {@code 0} to never expire. */
    private final long defaultExpiryMillis;

    public StashService(VoucherStorage storage, Scheduler scheduler, Logger logger, long defaultExpiryMillis) {
        this.storage = storage;
        this.scheduler = scheduler;
        this.logger = logger;
        this.defaultExpiryMillis = Math.max(0, defaultExpiryMillis);
    }

    /**
     * Queues {@code amount} copies of {@code voucherId} in {@code player}'s stash off the calling
     * thread. The optional {@code arg} is passed to the voucher's rewards as {@code %arg%} on claim.
     */
    public void stash(UUID player, String voucherId, int amount, @Nullable String arg, StashSource source) {
        long now = System.currentTimeMillis();
        Long expiresAt = defaultExpiryMillis > 0 ? now + defaultExpiryMillis : null;
        StashEntry entry = new StashEntry(UUID.randomUUID(), player, voucherId, amount, arg, source, now, expiresAt);
        scheduler.async(() -> {
            try {
                storage.addStash(entry);
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to stash voucher '" + voucherId + "' for " + player, ex);
            }
        });
    }
}
