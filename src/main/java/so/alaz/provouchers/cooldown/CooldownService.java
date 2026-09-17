package so.alaz.provouchers.cooldown;

import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.platform.CooldownManager;
import so.alaz.provouchers.platform.Scheduler;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Per-player, per-voucher cooldowns that survive restarts. An in-memory
 * {@link CooldownManager} answers the fast synchronous checks during a redeem,
 * while the same data is persisted to storage and re-loaded when a player joins.
 *
 * <p>Checks fail closed until that load succeeds: {@link #ensureLoaded} tells a redeem
 * whether a player's in-memory cooldowns can be trusted yet, and a failed load is retried
 * with backoff while the player stays online. Writes are best-effort: if one fails, the
 * cooldown still holds in memory on this server until it restarts.
 */
public final class CooldownService {

    private final CooldownManager memory;
    private final VoucherStorage storage;
    private final Scheduler scheduler;
    private final CooldownReadiness readiness = new CooldownReadiness();

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
            } catch (SQLException | RuntimeException ex) {
                // Best-effort: the cooldown still holds in memory for this session.
            }
        });
    }

    /**
     * Whether the player's stored cooldowns are loaded, so {@link #isOnCooldown} can be trusted for
     * them. When they are not, starts a load unless one is already running, so a redeem made after a
     * load ran out of attempts tries storage again.
     */
    public boolean ensureLoaded(UUID player) {
        hydrate(player);
        return readiness.isReady(player);
    }

    /**
     * Loads a player's stored cooldowns into memory (call on join), unless they are loaded or a load
     * is already running. A failed attempt is retried with backoff until one succeeds, the attempts
     * run out, or the player leaves.
     */
    public void hydrate(UUID player) {
        CooldownReadiness.Load load = readiness.begin(player);
        if (load != null) {
            scheduler.async(() -> attemptLoad(load));
        }
    }

    /**
     * Marks a player's cooldowns as needing a reload (call on quit) and stops any load still running.
     * Their in-memory entries stay, so a cooldown whose write has not reached storage survives a quick
     * rejoin; the next join loads again from storage (which another server sharing the database may have
     * updated) and merges, keeping the later expiry.
     */
    public void unload(UUID player) {
        readiness.forget(player);
    }

    /** One load attempt (async context). A failure schedules the next attempt while the load is current. */
    private void attemptLoad(CooldownReadiness.Load load) {
        if (!readiness.isCurrent(load)) {
            return; // The player left, so a pending retry stops here.
        }
        UUID player = load.player();
        long now = System.currentTimeMillis();
        Map<String, Long> active;
        try {
            active = storage.activeCooldowns(player, now);
        } catch (SQLException | RuntimeException ex) {
            Duration delay = readiness.fail(load);
            if (delay != null) {
                scheduler.delayedAsync(() -> attemptLoad(load), delay.toMillis(), TimeUnit.MILLISECONDS);
            }
            return;
        }
        // Extend rather than set, so a cooldown applied during the load is never shortened.
        readiness.succeed(load, () -> active.forEach((voucherId, expiresAt) ->
            memory.extendMillis(key(player, voucherId), expiresAt - now)));
    }

    private static String key(UUID player, String voucherId) {
        return player + ":" + voucherId;
    }
}
