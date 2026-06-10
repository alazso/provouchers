package so.alaz.provouchers.platform;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks keyed cooldowns against the system clock. Keys are any object with sensible
 * {@code equals}/{@code hashCode} - typically a composite of player UUID and voucher id.
 * Thread-safe.
 */
public final class CooldownManager {

    private final ConcurrentHashMap<Object, Long> expiries = new ConcurrentHashMap<>();

    /** Starts (or restarts) a cooldown for the key lasting the given milliseconds. */
    public void setMillis(Object key, long millis) {
        if (millis <= 0) {
            expiries.remove(key);
        } else {
            expiries.put(key, System.currentTimeMillis() + millis);
        }
    }

    /** Whether the key is currently on cooldown. */
    public boolean isOnCooldown(Object key) {
        return remainingMillis(key) > 0;
    }

    /** Time remaining for the key, or {@link Duration#ZERO} if not on cooldown. */
    public Duration remaining(Object key) {
        return Duration.ofMillis(remainingMillis(key));
    }

    private long remainingMillis(Object key) {
        Long expiry = expiries.get(key);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            expiries.remove(key);
            return 0;
        }
        return remaining;
    }
}
