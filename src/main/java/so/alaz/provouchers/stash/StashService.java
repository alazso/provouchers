package so.alaz.provouchers.stash;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.metrics.MetricCounters;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The async gateway to the Stash. Queueing, listing, counting, and the atomic claim all run on the
 * async scheduler; storage failures are logged rather than thrown. Every delivery is its own immutable
 * row and the menu shows rows grouped into {@link StashCard}s. A claim removes a card's rows atomically,
 * one delete by id each, and runs the voucher through {@link RedeemHandler}'s claim path (conditions,
 * grant, effects) for only the rows it removed, so a row is granted at most once even under a double
 * click or two sessions, and a row that could not be granted is restored exactly as it was.
 */
public final class StashService {

    private final VoucherStorage storage;
    private final VoucherRegistry registry;
    private final RedeemHandler redeemHandler;
    private final Scheduler scheduler;
    private final Text text;
    private final Messages messages;
    private final Logger logger;
    private final MetricCounters counters;
    /** Default lifetime for new entries in millis, or {@code 0} to never expire. */
    private final long defaultExpiryMillis;

    public StashService(VoucherStorage storage, VoucherRegistry registry, RedeemHandler redeemHandler,
                        Scheduler scheduler, Text text, Messages messages, Logger logger,
                        MetricCounters counters, long defaultExpiryMillis) {
        this.storage = storage;
        this.registry = registry;
        this.redeemHandler = redeemHandler;
        this.scheduler = scheduler;
        this.text = text;
        this.messages = messages;
        this.logger = logger;
        this.counters = counters;
        this.defaultExpiryMillis = Math.max(0, defaultExpiryMillis);
    }

    /**
     * Queues {@code amount} copies of {@code voucherId} in {@code player}'s stash off the calling
     * thread. The optional {@code arg} is passed to the voucher's rewards as {@code %arg%} on claim.
     * Each call inserts its own row, so concurrent deliveries never interfere.
     */
    public void stash(UUID player, String voucherId, int amount, @Nullable String arg, StashSource source) {
        long now = System.currentTimeMillis();
        Long expiresAt = defaultExpiryMillis > 0 ? now + defaultExpiryMillis : null;
        // The arg column is VARCHAR(255); cap it here so an over-long argument cannot fail the insert.
        String capped = arg != null && arg.length() > 255 ? arg.substring(0, 255) : arg;
        StashEntry entry = new StashEntry(UUID.randomUUID(), player, voucherId, amount, capped, source, now, expiresAt);
        scheduler.async(() -> {
            try {
                storage.addStash(entry);
                counters.recordStashed(amount);
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to stash voucher '" + voucherId + "' for " + player, ex);
            }
        });
    }

    /**
     * Loads a player's live entries off-thread and groups them into the cards the menu shows, leaving out
     * cards whose voucher is no longer configured, then runs {@code callback} on the async thread.
     */
    public void cards(UUID player, Consumer<List<StashCard>> callback) {
        scheduler.async(() -> {
            List<StashEntry> entries;
            try {
                entries = storage.listStash(player, System.currentTimeMillis());
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to read the stash for " + player, ex);
                entries = List.of();
            }
            callback.accept(StashCard.group(entries).stream()
                .filter(card -> registry.getVoucher(card.voucherId()).isPresent())
                .toList());
        });
    }

    /** Removes every lapsed entry. Blocking; intended to be called from an async context (the sweeper). */
    public void pruneExpired() {
        try {
            counters.recordStashExpired(storage.pruneExpiredStash(System.currentTimeMillis()));
        } catch (SQLException | RuntimeException ex) {
            logger.log(Level.WARNING, "Failed to prune expired stash entries", ex);
        }
    }

    /**
     * Counts the cards the menu would show a player (not stored rows) off-thread, then runs
     * {@code callback} on the async thread.
     */
    public void count(UUID player, IntConsumer callback) {
        cards(player, cards -> callback.accept(cards.size()));
    }

    /**
     * Claims one card for an online player. Call this on the player's thread. The card's rows are removed
     * atomically <em>first</em>, so its conditions and the pre-redeem event run only when this call removed
     * at least one, and exactly the sum of the removed rows is granted on the player's thread: a row
     * another claim took is not granted twice, and a row queued after the menu was built is not touched.
     * If the voucher's conditions now fail, or the player logs out before the grant, exactly the removed
     * rows are restored so the reward is never lost. {@code onDone} (a GUI refresh) runs on the player's
     * thread.
     */
    public void claim(Player player, StashCard card, Runnable onDone) {
        Voucher voucher = registry.getVoucher(card.voucherId()).orElse(null);
        if (voucher == null) {
            // The voucher was deleted since the entries were queued: drop the stale rows and explain.
            discard(card);
            text.send(player, messages.get(player, "stash.not-configured"));
            onDone.run();
            return;
        }
        if (card.isExpired(System.currentTimeMillis())) {
            text.send(player, messages.get(player, "stash.already-claimed"));
            onDone.run();
            return;
        }
        scheduler.async(() -> {
            StashCard taken = take(card);
            if (taken == null) {
                scheduler.entity(player, () -> {
                    text.send(player, messages.get(player, "stash.already-claimed"));
                    onDone.run();
                });
                return;
            }
            // Exactly one of these runs, so every removed row is either granted or restored, once.
            scheduler.entity(player,
                () -> {
                    if (redeemHandler.canClaim(player, voucher)) {
                        redeemHandler.grantClaim(player, voucher, taken.amount(), taken.arg());
                        text.send(player, messages.get(player, "stash.claimed", "voucher", voucher.id()));
                    } else {
                        restore(taken);   // conditions failed after claiming: put these rows back
                    }
                    onDone.run();
                },
                () -> restore(taken));     // the player left before the grant: put these rows back
        });
    }

    /**
     * Removes a card's rows, each with its own atomic delete by id, and returns a card of just the rows
     * this call removed, or {@code null} if it removed none. Rows another claim removed first are
     * skipped, so overlapping claims split a card's rows without overlap. Stops at a storage failure,
     * keeping the rows already removed for the caller to grant or restore; the rest stay queued.
     * Blocking; call it from an async task.
     */
    @Nullable
    private StashCard take(StashCard card) {
        List<StashEntry> removed = new ArrayList<>(card.entries().size());
        for (StashEntry entry : card.entries()) {
            try {
                if (storage.claimStash(entry.id())) {
                    removed.add(entry);
                }
            } catch (SQLException | RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to claim stash entry " + entry.id(), ex);
                break;
            }
        }
        return removed.isEmpty() ? null : new StashCard(removed);
    }

    /**
     * Re-inserts removed rows exactly as they were read, so a reward is never lost when a grant could not
     * complete. The primary key keeps a row from being restored twice, and a restore never changes an
     * amount. The work hops to the async scheduler, so this is safe from any thread, including a retired
     * callback that runs off the region thread.
     */
    private void restore(StashCard taken) {
        scheduler.async(() -> {
            for (StashEntry entry : taken.entries()) {
                try {
                    storage.restoreStash(entry);
                } catch (SQLException | RuntimeException ex) {
                    logger.log(Level.WARNING, "Failed to restore stash entry " + entry.id() + " ("
                        + entry.amount() + "x '" + entry.voucherId() + "' for " + entry.player() + ")", ex);
                }
            }
        });
    }

    /** Drops a card's stale rows (their voucher no longer exists); best effort. */
    private void discard(StashCard card) {
        scheduler.async(() -> {
            for (StashEntry entry : card.entries()) {
                try {
                    storage.claimStash(entry.id());
                } catch (SQLException | RuntimeException ex) {
                    logger.log(Level.WARNING, "Failed to discard stale stash entry " + entry.id(), ex);
                    return;
                }
            }
        });
    }

    /**
     * Claims every card in {@code cards} in turn for an online player, each as {@link #claim} does, then
     * sends a single summary and runs {@code onDone} (a GUI refresh). Cards whose conditions fail are
     * skipped (their own denial message is shown). Call this on the player's thread.
     */
    public void claimAll(Player player, List<StashCard> cards, Runnable onDone) {
        claimAllStep(player, new ArrayDeque<>(cards), new int[]{0}, onDone);
    }

    private void claimAllStep(Player player, Deque<StashCard> queue, int[] granted, Runnable onDone) {
        // Skip cards whose voucher is gone or already lapsed, iteratively so a long run of skippable
        // cards cannot grow the stack. The recursion into the next card happens through the scheduler
        // below, never synchronously.
        StashCard card = null;
        Voucher voucher = null;
        StashCard candidate;
        while ((candidate = queue.poll()) != null) {
            Voucher resolved = registry.getVoucher(candidate.voucherId()).orElse(null);
            if (resolved != null && !candidate.isExpired(System.currentTimeMillis())) {
                card = candidate;
                voucher = resolved;
                break;
            }
        }
        if (card == null) {
            if (granted[0] > 0) {
                text.send(player, messages.get(player, "stash.claimed-all", "count", granted[0]));
            }
            onDone.run();
            return;
        }
        StashCard claiming = card;
        Voucher claimingVoucher = voucher;
        scheduler.async(() -> {
            StashCard taken = take(claiming);
            scheduler.entity(player,
                () -> {
                    if (taken != null && redeemHandler.canClaim(player, claimingVoucher)) {
                        redeemHandler.grantClaim(player, claimingVoucher, taken.amount(), taken.arg());
                        granted[0]++;
                    } else if (taken != null) {
                        restore(taken);   // conditions failed after claiming: put these rows back
                    }
                    claimAllStep(player, queue, granted, onDone);
                },
                () -> {
                    // The player left mid-claim-all: put back only the rows this step removed, if any.
                    if (taken != null) {
                        restore(taken);
                    }
                });
        });
    }
}
