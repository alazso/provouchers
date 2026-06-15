package so.alaz.provouchers.stash;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The async gateway to the Stash. Queueing, listing, counting, and the atomic claim all run on the
 * async scheduler; storage failures are logged rather than thrown. A claim runs the voucher through
 * {@link RedeemHandler}'s claim path (conditions, grant, effects) only after the entry is removed
 * atomically, so an entry is granted at most once even under a double click or two sessions.
 */
public final class StashService {

    private final VoucherStorage storage;
    private final VoucherRegistry registry;
    private final RedeemHandler redeemHandler;
    private final Scheduler scheduler;
    private final Text text;
    private final Messages messages;
    private final Logger logger;
    /** Default lifetime for new entries in millis, or {@code 0} to never expire. */
    private final long defaultExpiryMillis;

    public StashService(VoucherStorage storage, VoucherRegistry registry, RedeemHandler redeemHandler,
                        Scheduler scheduler, Text text, Messages messages, Logger logger,
                        long defaultExpiryMillis) {
        this.storage = storage;
        this.registry = registry;
        this.redeemHandler = redeemHandler;
        this.scheduler = scheduler;
        this.text = text;
        this.messages = messages;
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

    /** Loads a player's live entries off-thread, then runs {@code callback} on the async thread. */
    public void entries(UUID player, Consumer<List<StashEntry>> callback) {
        scheduler.async(() -> {
            List<StashEntry> entries;
            try {
                entries = storage.listStash(player, System.currentTimeMillis());
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to read the stash for " + player, ex);
                entries = List.of();
            }
            callback.accept(entries);
        });
    }

    /** Removes every lapsed entry. Blocking; intended to be called from an async context (the sweeper). */
    public void pruneExpired() {
        try {
            storage.pruneExpiredStash(System.currentTimeMillis());
        } catch (SQLException | RuntimeException ex) {
            logger.log(Level.WARNING, "Failed to prune expired stash entries", ex);
        }
    }

    /** Counts a player's live entries off-thread, then runs {@code callback} on the async thread. */
    public void count(UUID player, IntConsumer callback) {
        scheduler.async(() -> {
            int count;
            try {
                count = storage.countStash(player, System.currentTimeMillis());
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to count the stash for " + player, ex);
                count = 0;
            }
            callback.accept(count);
        });
    }

    /**
     * Claims one entry for an online player. Call this on the player's thread. It checks the claim is
     * allowed, removes the entry atomically off-thread, grants the reward back on the player's thread,
     * and finally runs {@code onDone} (a GUI refresh) on the player's thread.
     */
    public void claim(Player player, StashEntry entry, Runnable onDone) {
        Voucher voucher = registry.getVoucher(entry.voucherId()).orElse(null);
        if (voucher == null) {
            // The voucher was deleted since the entry was queued: drop the stale entry and explain.
            scheduler.async(() -> {
                try {
                    storage.claimStash(entry.id());
                } catch (SQLException | RuntimeException ignored) {
                    // Best effort; the entry will simply show again until cleanup succeeds.
                }
            });
            text.send(player, messages.get(player, "stash.not-configured"));
            onDone.run();
            return;
        }
        if (entry.isExpired(System.currentTimeMillis()) || !redeemHandler.canClaim(player, voucher)) {
            if (entry.isExpired(System.currentTimeMillis())) {
                text.send(player, messages.get(player, "stash.already-claimed"));
            }
            onDone.run();
            return;
        }
        scheduler.async(() -> {
            boolean claimed;
            try {
                claimed = storage.claimStash(entry.id());
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to claim stash entry " + entry.id(), ex);
                claimed = false;
            }
            boolean finalClaimed = claimed;
            scheduler.entity(player, () -> {
                if (finalClaimed) {
                    redeemHandler.grantClaim(player, voucher, entry.amount(), entry.arg());
                    text.send(player, messages.get(player, "stash.claimed", "voucher", voucher.id()));
                } else {
                    text.send(player, messages.get(player, "stash.already-claimed"));
                }
                onDone.run();
            });
        });
    }

    /**
     * Claims every entry in {@code entries} in turn for an online player, each atomically, then sends a
     * single summary and runs {@code onDone} (a GUI refresh). Entries whose conditions fail are skipped
     * (their own denial message is shown). Call this on the player's thread.
     */
    public void claimAll(Player player, List<StashEntry> entries, Runnable onDone) {
        claimAllStep(player, new ArrayDeque<>(entries), new int[]{0}, onDone);
    }

    private void claimAllStep(Player player, Deque<StashEntry> queue, int[] granted, Runnable onDone) {
        StashEntry entry = queue.poll();
        if (entry == null) {
            if (granted[0] > 0) {
                text.send(player, messages.get(player, "stash.claimed-all", "count", granted[0]));
            }
            onDone.run();
            return;
        }
        Voucher voucher = registry.getVoucher(entry.voucherId()).orElse(null);
        if (voucher == null || entry.isExpired(System.currentTimeMillis())
            || !redeemHandler.canClaim(player, voucher)) {
            claimAllStep(player, queue, granted, onDone);
            return;
        }
        scheduler.async(() -> {
            boolean claimed;
            try {
                claimed = storage.claimStash(entry.id());
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to claim stash entry " + entry.id(), ex);
                claimed = false;
            }
            boolean finalClaimed = claimed;
            scheduler.entity(player, () -> {
                if (finalClaimed) {
                    redeemHandler.grantClaim(player, voucher, entry.amount(), entry.arg());
                    granted[0]++;
                }
                claimAllStep(player, queue, granted, onDone);
            });
        });
    }
}
