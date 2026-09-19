package so.alaz.provouchers.stash;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.metrics.MetricCounters;
import so.alaz.provouchers.platform.PendingSettlements;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.TaskGate;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.sql.SQLException;
import java.time.Duration;
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
 * The async gateway to the Stash. Queueing, listing, counting, and row removal run on the async
 * scheduler; storage failures are logged, not thrown. Every delivery is its own immutable row, and the
 * menu groups rows into {@link StashCard}s.
 *
 * <p>A claim removes a card's rows with one atomic delete by id each and grants only the rows it removed,
 * through {@link RedeemHandler}'s claim path, so a row is granted at most once. Removed rows are restored
 * exactly as they were when the claim is refused, or when the player leaves or the plugin disables
 * before the grant. Paper runs neither callback of a hop still scheduled when the plugin disables, so a
 * claim's hop to the player is tracked until it starts, and {@link #settleForShutdown} restores the rows
 * of every hop that never ran.
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
    /** Admits the async row removals of claims until the plugin disables. */
    private final TaskGate removals = new TaskGate();
    /** Removed rows handed to the player's thread that have neither been granted nor restored yet. */
    private final PendingSettlements handOffs = new PendingSettlements();

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
     * Queues {@code amount} copies of {@code voucherId} in {@code player}'s stash as a new row, off the
     * calling thread, or on it while the plugin disables. The optional {@code arg} is passed to the
     * voucher's rewards as {@code %arg%} on claim.
     */
    public void stash(UUID player, String voucherId, int amount, @Nullable String arg, StashSource source) {
        long now = System.currentTimeMillis();
        Long expiresAt = defaultExpiryMillis > 0 ? now + defaultExpiryMillis : null;
        // The arg column is VARCHAR(255); a longer value fails the insert.
        String capped = arg != null && arg.length() > 255 ? arg.substring(0, 255) : arg;
        StashEntry entry = new StashEntry(UUID.randomUUID(), player, voucherId, amount, capped, source, now, expiresAt);
        scheduler.asyncOrRun(() -> {
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
     * cards whose voucher is not loaded, then runs {@code callback} on the async thread.
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

    /** Removes every lapsed entry. Blocking; call it from an async task. */
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
     * Claims one card for an online player; call it on the player's thread. The card's rows are removed
     * first, off-thread, so the voucher's conditions and the pre-redeem event run only when this call
     * removed at least one row, and only those rows are granted, on the player's thread. A row queued
     * after the card was built is not touched. Once shutdown settlement begins, a claim removes no rows.
     * {@code onDone} runs on the player's thread.
     */
    public void claim(Player player, StashCard card, Runnable onDone) {
        Voucher voucher = registry.getVoucher(card.voucherId()).orElse(null);
        if (voucher == null) {
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
            if (!removals.enter()) {
                return;   // the plugin is disabling: remove no rows
            }
            try {
                StashCard taken = take(card);
                if (taken == null) {
                    scheduler.entity(player, () -> {
                        text.send(player, messages.get(player, "stash.already-claimed"));
                        onDone.run();
                    });
                    return;
                }
                // Whichever of the two callbacks or the shutdown settlement starts this entry first handles
                // the rows, so they are granted or restored exactly once.
                PendingSettlements.Entry pending = handOffs.track(() -> restore(taken));
                scheduler.entity(player,
                    () -> {
                        if (!pending.start()) {
                            return;   // the shutdown settlement restores these rows
                        }
                        if (grantOrRestore(player, voucher, taken)) {
                            text.send(player, messages.get(player, "stash.claimed", "voucher", voucher.id()));
                        }
                        onDone.run();
                    },
                    () -> {
                        if (pending.start()) {
                            restore(taken);     // the player left, or the plugin is disabling
                        }
                    });
            } finally {
                removals.exit();
            }
        });
    }

    /**
     * On the player's thread, grants a taken card when its voucher can be claimed now, otherwise restores
     * its rows, and returns whether it granted. A failure before granting starts restores the rows too.
     * Once granting starts the rows count as consumed: a failure part-way through is logged, not restored,
     * so no reward is granted twice.
     */
    private boolean grantOrRestore(Player player, Voucher voucher, StashCard taken) {
        boolean granting = false;
        try {
            if (!redeemHandler.canClaim(player, voucher)) {
                restore(taken);
                return false;
            }
            granting = true;
            redeemHandler.grantClaim(player, voucher, taken.amount(), taken.arg());
            return true;
        } catch (RuntimeException ex) {
            if (!granting) {
                restore(taken);
            }
            logger.log(Level.WARNING, "Failed to claim " + taken.amount() + "x '" + voucher.id() + "' from the "
                + "stash of " + player.getName() + (granting ? "; the rewards may be partly granted" : ""), ex);
            return granting;
        }
    }

    /**
     * Removes a card's rows, each with its own atomic delete by id, and returns a card of only the rows
     * this call removed, or {@code null} if it removed none. Rows another claim removed first are skipped.
     * Stops at a storage failure and returns the rows already removed; the rest stay queued. Blocking;
     * call it from an async task.
     */
    @Nullable
    private StashCard take(StashCard card) {
        List<StashEntry> removed = new ArrayList<>(card.entries().size());
        for (StashEntry entry : card.entries()) {
            try {
                if (storage.claimStash(entry)) {
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
     * Re-inserts removed rows exactly as they were read; the primary key makes a second restore of a row a
     * no-op. Runs on the async scheduler, or on the calling thread while the plugin disables, so it is safe
     * from any thread, including a retired callback running off the region thread.
     */
    private void restore(StashCard taken) {
        scheduler.asyncOrRun(() -> {
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

    /** Deletes the rows of a card whose voucher is not loaded, stopping at the first storage failure. */
    private void discard(StashCard card) {
        scheduler.async(() -> {
            for (StashEntry entry : card.entries()) {
                try {
                    storage.claimStash(entry);
                } catch (SQLException | RuntimeException ex) {
                    logger.log(Level.WARNING, "Failed to discard stale stash entry " + entry.id(), ex);
                    return;
                }
            }
        });
    }

    /**
     * Claims every card in {@code cards} in turn for an online player, each as {@link #claim} does, then
     * sends one summary if any card was granted and runs {@code onDone}. Cards whose conditions fail are
     * skipped after their own denial message. Call it on the player's thread.
     */
    public void claimAll(Player player, List<StashCard> cards, Runnable onDone) {
        claimAllStep(player, new ArrayDeque<>(cards), new int[]{0}, onDone);
    }

    private void claimAllStep(Player player, Deque<StashCard> queue, int[] granted, Runnable onDone) {
        // Unloaded and lapsed cards are passed over in this loop, and the next step runs from a scheduler
        // callback, never synchronously, so a long run of cards cannot grow the stack.
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
            if (!removals.enter()) {
                return;   // the plugin is disabling: end the run
            }
            try {
                StashCard taken = take(claiming);
                // Only removed rows need settling; the hop still runs to continue with the next card.
                PendingSettlements.Entry pending = taken != null ? handOffs.track(() -> restore(taken)) : null;
                scheduler.entity(player,
                    () -> {
                        if (pending != null && !pending.start()) {
                            return;   // the shutdown settlement restores these rows
                        }
                        if (taken != null && grantOrRestore(player, claimingVoucher, taken)) {
                            granted[0]++;
                        }
                        claimAllStep(player, queue, granted, onDone);
                    },
                    () -> {
                        // The player left, or the plugin is disabling: restore the rows this step removed, if any.
                        if (pending != null && pending.start()) {
                            restore(taken);
                        }
                    });
            } finally {
                removals.exit();
            }
        });
    }

    /**
     * Restores the rows of every claim the plugin would otherwise lose by disabling. Call it from
     * {@code onDisable}, before storage closes. Stops claims from removing further rows and waits up to
     * {@code wait} for the removals already running, which restore their own rows because their hop to the
     * player cannot be scheduled. Then restores, on the calling thread, the rows of every claim whose hop
     * was scheduled but never ran. Logs only when rows were restored, a removal was still running, or a
     * row failed to restore.
     */
    public void settleForShutdown(Duration wait) {
        int running = removals.close(wait);
        int restored = handOffs.settleAll(ex -> logger.log(Level.WARNING,
            "Failed to restore the rows of an unfinished stash claim during shutdown", ex));
        if (running > 0) {
            logger.warning("Restored the rows of " + restored + " unfinished stash claim(s) during shutdown, but "
                + running + " claim(s) were still removing rows after " + wait.toMillis() + " ms. Each restores "
                + "its rows when it finishes, which fails once storage has closed.");
        } else if (restored > 0) {
            logger.info("Restored the rows of " + restored + " unfinished stash claim(s) during shutdown.");
        }
    }
}
