package so.alaz.provouchers.cooldown;

import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.platform.CooldownManager;
import so.alaz.provouchers.platform.Scheduler;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, per-voucher cooldowns that survive restarts. An in-memory
 * {@link CooldownManager} answers the fast synchronous checks during a redeem,
 * while the same data is persisted to storage and re-loaded when a player joins.
 *
 * <p>Persistence is best-effort: if a storage write or read fails, the cooldown
 * still works in memory for the current session.
 */
public final class CooldownService {

    private final CooldownManager memory;
    private final VoucherStorage storage;
    private final Scheduler scheduler;

    public CooldownService(CooldownManager memory, VoucherStorage storage, Scheduler scheduler) {
        this.memory = memory;
        this.storage = storage;
        this.scheduler = scheduler;
    }

    /** Whether this player is currently on cooldown for the voucher. */
    public boolean isOnCooldown(UUID player, String voucherId) {
        return memory.isOnCooldown(key(player, voucherId));
    }

    /** Time remaining on this player's cooldown for the voucher, or zero. */
    public Duration remaining(UUID player, String voucherId) {
        return memory.remaining(key(player, voucherId));
    }

    /** Starts a cooldown of {@code seconds} and persists it off-thread. */
    public void apply(UUID player, String voucherId, long seconds) {
        long millis = seconds * 1000L;
        memory.setMillis(key(player, voucherId), millis);
        long expiresAt = System.currentTimeMillis() + millis;
        scheduler.async(() -> {
            try {
                storage.setCooldown(player, voucherId, expiresAt);
            } catch (SQLException ex) {
                // Best-effort: the cooldown still holds in memory for this session.
            }
        });
    }

    /** Loads a player's stored cooldowns into memory (call on join). */
    public void hydrate(UUID player) {
        scheduler.async(() -> {
            long now = System.currentTimeMillis();
            Map<String, Long> active;
            try {
                active = storage.activeCooldowns(player, now);
            } catch (SQLException ex) {
                return;
            }
            active.forEach((voucherId, expiresAt) -> {
                long remaining = expiresAt - now;
                if (remaining > 0) {
                    memory.setMillis(key(player, voucherId), remaining);
                }
            });
        });
    }

    private static String key(UUID player, String voucherId) {
        return player + ":" + voucherId;
    }
}
