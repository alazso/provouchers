package so.alaz.provouchers.cooldown;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have their stored cooldowns loaded into memory, and the load running for those
 * who do not. Until a player is ready, their in-memory cooldowns cannot be trusted. A load retries a
 * failed attempt after a doubling delay, up to {@link #MAX_ATTEMPTS} attempts. Forgetting a player (on
 * quit) ends their load, so a pending retry or a late result is discarded, even after they rejoin.
 *
 * <p>Thread-safe: every operation holds this tracker's lock, including the merge passed to
 * {@link #succeed}, so a merge and a quit never interleave.
 */
final class CooldownReadiness {

    /** Attempts per load, the first included; after the last fails, a later {@link #begin} starts over. */
    static final int MAX_ATTEMPTS = 5;
    /** Delay before the first retry; each later retry waits twice as long as the one before. */
    static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(2);

    /**
     * One player's load: the first attempt and its retries. Compared by identity, so a load begun after
     * a quit is never mistaken for the one before it.
     */
    static final class Load {

        private final UUID player;
        private int failures;

        private Load(UUID player) {
            this.player = player;
        }

        UUID player() {
            return player;
        }
    }

    private final Map<UUID, Load> loading = new HashMap<>();
    private final Set<UUID> ready = new HashSet<>();

    /** Whether the player's stored cooldowns have loaded. */
    synchronized boolean isReady(UUID player) {
        return ready.contains(player);
    }

    /** Starts a load for the player, or returns {@code null} if they are ready or already loading. */
    @Nullable
    synchronized Load begin(UUID player) {
        if (ready.contains(player) || loading.containsKey(player)) {
            return null;
        }
        Load load = new Load(player);
        loading.put(player, load);
        return load;
    }

    /** Whether the load is still running: it has not succeeded, run out of attempts, or been forgotten. */
    synchronized boolean isCurrent(Load load) {
        return loading.get(load.player) == load;
    }

    /**
     * Ends a successful load: runs {@code merge}, then marks the player ready. Returns {@code false},
     * running nothing, when the load is no longer current.
     */
    synchronized boolean succeed(Load load, Runnable merge) {
        if (!isCurrent(load)) {
            return false;
        }
        merge.run();
        loading.remove(load.player);
        ready.add(load.player);
        return true;
    }

    /**
     * Records a failed attempt and returns the delay before the next one, or {@code null} when the load
     * is over: no longer current, or out of attempts, which ends it with the player still not ready.
     */
    @Nullable
    synchronized Duration fail(Load load) {
        if (!isCurrent(load)) {
            return null;
        }
        load.failures++;
        if (load.failures >= MAX_ATTEMPTS) {
            loading.remove(load.player);
            return null;
        }
        return retryDelay(load.failures);
    }

    /** Forgets the player (on quit): ends any load and clears their readiness. */
    synchronized void forget(UUID player) {
        loading.remove(player);
        ready.remove(player);
    }

    /** The delay before the retry after {@code failures} failed attempts: 2s, then 4s, 8s, and so on. */
    static Duration retryDelay(int failures) {
        return FIRST_RETRY_DELAY.multipliedBy(1L << (failures - 1));
    }
}
