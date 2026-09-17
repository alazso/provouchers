package so.alaz.provouchers.platform;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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

    /**
     * Extends the key's cooldown to last at least the given milliseconds, keeping the later expiry,
     * so it never shortens a longer cooldown already running.
     */
    public void extendMillis(Object key, long millis) {
        if (millis > 0) {
            expiries.merge(key, System.currentTimeMillis() + millis, Math::max);
        }
    }

    /** Removes the cooldown of every key that matches, such as all of one player's keys. */
    public void removeIf(Predicate<Object> key) {
        expiries.keySet().removeIf(key);
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
