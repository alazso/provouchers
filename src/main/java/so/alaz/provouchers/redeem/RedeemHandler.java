package so.alaz.provouchers.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.listener.FireworkGuardListener;
import so.alaz.provouchers.antidupe.ClaimOutcome;
import so.alaz.provouchers.antidupe.DupeDetector;
import so.alaz.provouchers.antidupe.StampStatus;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.metrics.MetricCounters;
import so.alaz.provouchers.api.event.VoucherCodePreRedeemEvent;
import so.alaz.provouchers.api.event.VoucherCodeRedeemEvent;
import so.alaz.provouchers.api.event.VoucherPreRedeemEvent;
import so.alaz.provouchers.api.event.VoucherRedeemEvent;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSelection;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.reward.WebhookSpec;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.storage.VoucherStorage.UseLimit;
import so.alaz.provouchers.storage.VoucherStorage.UseReservation;
import so.alaz.provouchers.voucher.DefinedItem;
import so.alaz.provouchers.voucher.FireworkSpec;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherEffects;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.provouchers.util.Expiry;
import so.alaz.provouchers.condition.Condition;
import so.alaz.provouchers.condition.ConditionContext;
import so.alaz.provouchers.condition.ConditionRegistry;
import so.alaz.provouchers.condition.ConditionResult;
import so.alaz.provouchers.condition.Conditions;
import so.alaz.provouchers.cooldown.CooldownService;
import so.alaz.provouchers.cooldown.CooldownTiers;
import so.alaz.provouchers.gui.ConfirmGui;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Sounds;
import so.alaz.provouchers.platform.Text;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * The redemption pipeline shared by item vouchers and codes. Synchronous checks
 * (existence, expiry, game mode, cooldown, conditions) run on the caller's thread;
 * persistent checks run on the async scheduler, where use limits are reserved
 * atomically in storage and duplicate stamps are claimed, then control hops back
 * to the player's region to grant rewards.
 *
 * <p>An item voucher stays in the inventory until that hop: only then is it
 * located and removed, so a refusal needs no refund. A redeem that cannot be
 * delivered (the player logged out, or moved the item away) gives back its
 * reserved uses and stamp claim instead of consuming them.
 *
 * <p>A stamped item's id enters a {@link ClaimLedger} before its redeem touches
 * storage and leaves once the claim is delivered or given back, so no other click
 * on this server takes an unsettled claim for a duplicate. A claim give-back that
 * fails is retried with backoff, and the item is refused as unverifiable meanwhile.
 */
public final class RedeemHandler {

    /**
     * How long an unreleased in-flight redeem blocks its voucher: a safety net against a missed release,
     * sized above the slowest realistic verification, including waits for a pooled connection.
     */
    private static final long IN_FLIGHT_LIFETIME_SECONDS = 300;

    /**
     * The delays before each retry of a stamp claim give-back that failed. When the last retry fails too,
     * the claim stays in storage and its id in the ledger, with a warning.
     */
    private static final List<Duration> CLAIM_RELEASE_RETRY_DELAYS = List.of(
        Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));

    /**
     * The raw {@link PlayerInventory} index of the off hand, per its slot contract (0-8 hotbar,
     * 9-35 storage, 36-39 armor, 40 off hand).
     */
    private static final int OFF_HAND_SLOT = 40;

    private final VoucherRegistry registry;
    private final VoucherStamp stamp;
    private final DupeDetector dupeDetector;
    private final VoucherStorage storage;
    private final RewardExecutor rewardExecutor;
    private final Scheduler scheduler;
    private final Text text;
    private final Messages messages;
    private final CooldownService cooldowns;
    private final ConditionRegistry conditions;
    private final MetricCounters counters;
    private final ComponentLogger logger;
    private final boolean removeOnDiscovery;
    private final boolean warningEnabled;
    private final String warningText;
    private final boolean notifyEnabled;
    private final boolean batchQuiet;
    private final ConfirmationTracker confirmations;
    /** One item redeem at a time per player and voucher, held from the click until it settles. */
    private final InFlightGuard inFlight = new InFlightGuard(IN_FLIGHT_LIFETIME_SECONDS);
    /** The stamp ids of unsettled item redeems, each owned by its redeem's in-flight ticket. */
    private final ClaimLedger ledger = new ClaimLedger();
    private final CooldownTiers cooldownTiers;
    /** The GUI confirm dialog, or {@code null} for the chat style. */
    @Nullable private final ConfirmGui confirmGui;

    public RedeemHandler(
        VoucherRegistry registry,
        VoucherStamp stamp,
        DupeDetector dupeDetector,
        VoucherStorage storage,
        RewardExecutor rewardExecutor,
        Scheduler scheduler,
        Text text,
        Messages messages,
        CooldownService cooldowns,
        ConditionRegistry conditions,
        MetricCounters counters,
        ComponentLogger logger,
        CooldownTiers cooldownTiers,
        @Nullable ConfirmGui confirmGui,
        boolean removeOnDiscovery,
        boolean warningEnabled,
        String warningText,
        boolean notifyEnabled,
        boolean batchQuiet,
        long confirmWindowSeconds
    ) {
        this.registry = registry;
        this.stamp = stamp;
        this.dupeDetector = dupeDetector;
        this.storage = storage;
        this.rewardExecutor = rewardExecutor;
        this.scheduler = scheduler;
        this.text = text;
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.conditions = conditions;
        this.counters = counters;
        this.logger = logger;
        this.cooldownTiers = cooldownTiers;
        this.confirmGui = confirmGui;
        this.removeOnDiscovery = removeOnDiscovery;
        this.warningEnabled = warningEnabled;
        this.warningText = warningText;
        this.notifyEnabled = notifyEnabled;
        this.batchQuiet = batchQuiet;
        this.confirmations = new ConfirmationTracker(confirmWindowSeconds);
    }

    /**
     * Attempts to redeem the voucher held in {@code hand}. When {@code sneaking} and the
     * voucher allows batch open, the whole stack is redeemed at once.
     */
    public void redeemHeldVoucher(Player player, EquipmentSlot hand, boolean sneaking) {
        redeemHeldVoucher(player, hand, sneaking, false);
    }

    /**
     * The shared body. {@code confirmed} is {@code true} on the re-entry from the two-step
     * confirm GUI, which carries the original {@code sneaking} so batch open survives and
     * bypasses the confirmation gate so a click after the window still redeems.
     */
    private void redeemHeldVoucher(Player player, EquipmentSlot hand, boolean sneaking, boolean confirmed) {
        ItemStack inHand = player.getInventory().getItem(hand);
        if (inHand == null || inHand.getType().isAir()) {
            return;
        }
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null || !stamp.isVoucher(meta)) {
            return;
        }
        Voucher voucher = registry.getVoucher(stamp.voucherId(meta)).orElse(null);
        if (voucher == null) {
            send(player, messages.get(player, "redeem.not-configured"));
            return;
        }
        if (voucher.unredeemable()) {
            send(player, messages.get(player, "redeem.unredeemable"));
            return;
        }
        if (!voucher.enabled()) {
            send(player, messages.get(player, "redeem.disabled"));
            return;
        }
        if (voucher.ownerOnly() && !ownsVoucher(player, meta)) {
            send(player, messages.get(player, "redeem.not-owner"));
            return;
        }
        Instant now = Instant.now();
        Long givenAtMillis = stamp.givenAt(meta);
        Instant reference = givenAtMillis != null ? Instant.ofEpochMilli(givenAtMillis) : now;
        if (Expiry.isExpired(Expiry.resolve(voucher.expiry(), reference), now)) {
            send(player, messages.get(player, "redeem.expired"));
            return;
        }
        Instant activeFrom = Expiry.resolveStart(voucher.activeFrom());
        if (activeFrom != null && now.isBefore(activeFrom)) {
            send(player, messages.get(player, "redeem.not-yet-active"));
            return;
        }
        if (!voucherPreChecks(player, voucher, hand, sneaking, confirmed)) {
            return;
        }

        if (sneaking && voucher.batchOpen()) {
            openWholeStack(player, hand, inHand, voucher);
            return;
        }

        // A second click while this voucher is mid-redeem is ignored: the first settles the cooldown and
        // limits it would otherwise race past. Taken even on the synchronous path, so an unstamped copy
        // cannot slip past a stamped one still being verified.
        InFlightGuard.Ticket ticket = inFlight.acquire(player.getUniqueId(), voucher.id());
        if (ticket == null) {
            return;
        }
        String uid = stamp.uid(meta);
        if (uid == null && !voucher.hasUseLimits()) {
            // Stackable, unlimited voucher: nothing persistent to check, commit on the current thread.
            try {
                consumeOne(player, hand, inHand);
                completeVoucherRedeem(player, voucher, null, false);
            } finally {
                inFlight.release(ticket);
            }
            return;
        }
        int slot = handSlot(player.getInventory(), hand);
        ItemStack original = inHand.asOne();
        Predicate<ItemStack> sameItem = uid != null ? item -> uid.equals(readUid(item)) : original::isSimilar;
        UUID playerId = player.getUniqueId();
        try {
            scheduler.async(() -> {
                Verdict verdict = verifyHeld(playerId, voucher, uid, ticket);
                handOff(player,
                    () -> deliverHeld(player, slot, voucher, uid, sameItem, verdict, ticket),
                    () -> abandon(playerId, voucher, verdict.claim(), verdict.reserved(), ticket));
            });
        } catch (RuntimeException ex) {
            // The task never ran, so nothing was reserved, claimed, or entered in the ledger.
            inFlight.release(ticket);
            throw ex;
        }
    }

    /**
     * The async verdict on a single item redeem, handed to the player's thread: the denial message key
     * when a use limit (or storage) refused it, the stamp status when the anti-dupe check refused it,
     * and otherwise the uses reserved and the stamp claim made ({@code null} for an unstamped item),
     * both to give back if the item is never delivered.
     */
    private record Verdict(@Nullable String denialKey, @Nullable StampStatus refusal, int reserved,
                           @Nullable Claim claim) {
    }

    /**
     * A stamp claim a redeem made: the item's unique id and the epoch millis it was claimed at, which
     * together with the player identify the claim, so only this redeem can give it back.
     */
    private record Claim(String uid, long claimedAt) {
    }

    /**
     * Verifies a single item redeem (async context). A stamped item's id first enters the claim ledger
     * for {@code ticket}; while another redeem of the same item holds it, this one is refused as
     * unverifiable with nothing reserved. Then the use is reserved when the voucher has limits, and the
     * item's stamp is checked and claimed. The use is reserved first so a limit denial never burns the
     * item's id, and it is given back when the stamp is refused.
     *
     * <p>A successful claim keeps its ledger entry until the redeem is delivered or given back. Every
     * refusal ends the entry, except a claim whose outcome is unknown: it may have been stored, so it is
     * given back like an undelivered claim, and that give-back ends the entry once it settles. A click
     * never releases a claim it did not make. Never throws: a storage failure becomes a denial or refusal.
     */
    private Verdict verifyHeld(UUID player, Voucher voucher, @Nullable String uid, InFlightGuard.Ticket ticket) {
        if (uid != null && !ledger.begin(uid, ticket)) {
            // An earlier redeem of this item has not settled its claim, which a check now would take for a
            // duplicate.
            return new Verdict(null, StampStatus.UNKNOWN, 0, null);
        }
        Reservation reservation = voucher.hasUseLimits()
            ? reserveVoucherUses(player, voucher, 1)
            : new Reservation(0, null);
        if (reservation.denialKey() != null) {
            if (uid != null) {
                ledger.end(uid, ticket);
            }
            return new Verdict(reservation.denialKey(), null, 0, null);
        }
        if (uid == null) {
            return new Verdict(null, null, reservation.reserved(), null);
        }
        StampStatus status = dupeDetector.check(uid);
        if (status == StampStatus.VALID) {
            Claim claim = new Claim(uid, System.currentTimeMillis());
            ClaimOutcome outcome = dupeDetector.claim(uid, player, claim.claimedAt());
            if (outcome == ClaimOutcome.CLAIMED) {
                return new Verdict(null, null, reservation.reserved(), claim);
            }
            // Give the use back before replying, so an immediate retry sees the true allowance.
            releaseUses(voucher.useKey(), player, reservation.reserved());
            if (outcome == ClaimOutcome.UNKNOWN) {
                // The claim may have been stored: give it back, which ends the entry once it settles.
                releaseClaim(player, voucher, claim, ticket, 0);
            } else {
                ledger.end(uid, ticket);
            }
            // A lost claim race, like a claim that may not have been stored, is unverifiable rather than a
            // proven duplicate.
            return new Verdict(null, StampStatus.UNKNOWN, 0, null);
        }
        releaseUses(voucher.useKey(), player, reservation.reserved());
        ledger.end(uid, ticket);
        return new Verdict(null, status, 0, null);
    }

    /**
     * Hands an async step's result to the player's region thread: runs {@code deliver} there, or
     * {@code giveBack} when the player is gone or the plugin is disabling. Exactly one of the two runs,
     * even when scheduling itself throws: then {@code giveBack} runs here, unless a callback already
     * started, before the failure propagates.
     */
    private void handOff(Player player, Runnable deliver, Runnable giveBack) {
        AtomicBoolean started = new AtomicBoolean();
        try {
            scheduler.entity(player,
                () -> {
                    if (started.compareAndSet(false, true)) {
                        deliver.run();
                    }
                },
                () -> {
                    if (started.compareAndSet(false, true)) {
                        giveBack.run();
                    }
                });
        } catch (RuntimeException ex) {
            if (started.compareAndSet(false, true)) {
                giveBack.run();
            }
            throw ex;
        }
    }

    /**
     * The player-thread half of a single item redeem. Replies to a denial or refusal; otherwise locates the
     * verified item (the preferred slot first, then anywhere in the inventory), removes exactly one, and
     * commits the redeem. An item that left the inventory meanwhile (dropped, stored, or on the cursor) is
     * not redeemed, and the reserved use and stamp claim are given back.
     *
     * <p>How the redeem settles is decided before each step that can throw, and runs in a finally. Until
     * the item is taken, it settles by giving back whatever the verdict holds, which for a denial or
     * refusal only releases the guard. Once the item is taken the claim stands, so it settles by ending the
     * claim's ledger entry and releasing the guard. A failing reply or reward therefore never leaks a
     * claim or reserved use, and never gives one back after delivery.
     */
    private void deliverHeld(Player player, int preferredSlot, Voucher voucher, @Nullable String uid,
                             Predicate<ItemStack> sameItem, Verdict verdict, InFlightGuard.Ticket ticket) {
        UUID playerId = player.getUniqueId();
        Runnable settle = () -> abandon(playerId, voucher, verdict.claim(), verdict.reserved(), ticket);
        try {
            if (verdict.denialKey() != null) {
                send(player, messages.get(player, verdict.denialKey()));
                return;
            }
            PlayerInventory inventory = player.getInventory();
            if (verdict.refusal() != null) {
                handleRejectedItem(player, inventory, preferredSlot, voucher, sameItem, verdict.refusal());
                return;
            }
            int slot = locate(inventory, preferredSlot, sameItem);
            if (slot < 0) {
                send(player, messages.get(player, "redeem.verify-failed"));
                return;
            }
            take(inventory, slot, 1);
            settle = () -> {
                if (verdict.claim() != null) {
                    ledger.end(verdict.claim().uid(), ticket);
                }
                inFlight.release(ticket);
            };
            // The use was recorded by the reservation, so committing records nothing further.
            completeVoucherRedeem(player, voucher, uid, false);
        } finally {
            settle.run();
        }
    }

    /**
     * Gives back what an undelivered redeem holds, its stamp claim and reserved uses, then releases its
     * in-flight guard, which is all it does when the redeem holds neither. The storage work runs on the
     * async scheduler, or on the calling thread while the plugin disables, so this is safe from a retired
     * callback on any thread.
     *
     * <p>The guard is released once the first attempt to give the claim back has finished. The claim's id
     * stays in the ledger until an attempt succeeds, so a retry meanwhile is refused as unverifiable
     * instead of finding the leftover claim and taking the item for a duplicate. Reserved uses are given
     * back once and never retried: repeating a release that did commit would take off a use the player
     * really made.
     */
    private void abandon(UUID player, Voucher voucher, @Nullable Claim claim, int reserved,
                         InFlightGuard.Ticket ticket) {
        if (claim == null && reserved <= 0) {
            inFlight.release(ticket);
            return;
        }
        try {
            scheduler.asyncOrRun(() -> {
                try {
                    if (claim != null) {
                        releaseClaim(player, voucher, claim, ticket, 0);
                    }
                    releaseUses(voucher.useKey(), player, reserved);
                } finally {
                    inFlight.release(ticket);
                }
            });
        } catch (RuntimeException ex) {
            // Never leave the voucher blocked, even when the give-back could not run. A claim that was not
            // given back keeps its ledger entry, so the item is refused as unverifiable, not as a duplicate.
            inFlight.release(ticket);
            throw ex;
        }
    }

    /**
     * One attempt to give back a stamp claim that was not delivered (async context, or the calling thread
     * while the plugin disables). Success, including finding the claim already gone, ends its ledger
     * entry. A storage failure keeps the entry, so this server refuses the item as unverifiable rather
     * than taking the leftover claim for a duplicate, and schedules the next attempt after the next delay
     * in {@link #CLAIM_RELEASE_RETRY_DELAYS}. When none is left, or none can be scheduled because the
     * plugin is disabling, the entry stays and a warning names the claim.
     */
    private void releaseClaim(UUID player, Voucher voucher, Claim claim, InFlightGuard.Ticket owner,
                              int failedAttempts) {
        if (dupeDetector.release(claim.uid(), player, claim.claimedAt())) {
            ledger.end(claim.uid(), owner);
            return;
        }
        if (failedAttempts < CLAIM_RELEASE_RETRY_DELAYS.size()) {
            Duration delay = CLAIM_RELEASE_RETRY_DELAYS.get(failedAttempts);
            try {
                scheduler.delayedAsync(() -> releaseClaim(player, voucher, claim, owner, failedAttempts + 1),
                    delay.toMillis(), TimeUnit.MILLISECONDS);
                return;
            } catch (RuntimeException ex) {
                // Nothing can be scheduled while the plugin disables, so this attempt was the last.
            }
        }
        logger.warn("Could not release the anti-dupe claim of voucher '{}' (uid {}) for player {} after {} "
                + "attempt(s). This server refuses that item as unverifiable until it restarts, and "
                + "afterwards it counts as already redeemed unless that uid's row is deleted from "
                + "provouchers_used_vouchers.",
            voucher.id(), claim.uid(), player, failedAttempts + 1);
    }

    /**
     * Uses reserved for an item redeem, and the denial message key for any shortfall ({@code null} when
     * every requested use fit). The reservation is atomic in storage, taken before anything is granted,
     * so concurrent redeems (on this server or another sharing the database) cannot exceed a cap.
     */
    private record Reservation(int reserved, @Nullable String denialKey) {
    }

    /**
     * Reserves up to {@code requested} uses of a limited voucher (async context). A shortfall carries
     * {@code redeem.use-limit} when the player's own cap ran out, else {@code redeem.global-limit}; a
     * storage failure reserves nothing and carries {@code redeem.verify-failed}.
     */
    private Reservation reserveVoucherUses(UUID player, Voucher voucher, int requested) {
        try {
            UseReservation result = storage.reserveUses(voucher.useKey(), player, requested,
                voucher.hasPerPlayerLimit() ? voucher.usesPerPlayer() : VoucherStorage.UNCAPPED,
                voucher.hasGlobalLimit() ? voucher.maxUses() : VoucherStorage.UNCAPPED);
            String denialKey = switch (result.limit()) {
                case NONE -> null;
                case PER_PLAYER -> "redeem.use-limit";
                case GLOBAL -> "redeem.global-limit";
            };
            return new Reservation(result.reserved(), denialKey);
        } catch (SQLException | RuntimeException ex) {
            return new Reservation(0, "redeem.verify-failed");
        }
    }

    /**
     * Gives back reserved uses (async context). Best-effort: if storage fails the uses stay counted,
     * which can cost the player a use but never grants an extra reward.
     */
    private void releaseUses(String key, UUID player, int count) {
        if (count <= 0) {
            return;
        }
        try {
            storage.releaseUses(key, player, count);
        } catch (SQLException | RuntimeException ignored) {
            // The uses stay reserved: a lost use, never an extra reward.
        }
    }

    /**
     * The source-agnostic gates a voucher redemption must pass before it commits:
     * game mode, cooldown, conditions, and the cancellable pre-redeem event. Replies
     * to the player on failure. Reused by the item path and (later) virtual vouchers.
     */
    private boolean voucherPreChecks(Player player, Voucher voucher, EquipmentSlot hand, boolean sneaking,
                                     boolean confirmed) {
        if (!gameModeAllowed(player)) {
            send(player, messages.get(player, "redeem.wrong-gamemode"));
            return false;
        }
        if (!player.hasPermission("provouchers.bypass.cooldown")) {
            if (voucher.cooldownSeconds() > 0 && !cooldowns.ensureLoaded(player.getUniqueId())) {
                // Until the player's stored cooldowns have loaded, memory could miss one, so refuse instead.
                send(player, messages.get(player, "redeem.verify-failed"));
                return false;
            }
            if (cooldowns.isOnCooldown(player.getUniqueId(), voucher.id())) {
                long seconds = cooldowns.remaining(player.getUniqueId(), voucher.id()).toSeconds();
                send(player, messages.get(player, "redeem.cooldown", "seconds", Math.max(1, seconds)));
                return false;
            }
        }
        ConditionResult conditionResult = evaluate(voucher.conditionMaps(), player);
        if (!conditionResult.getPassed()) {
            counters.recordConditionDenial();
            replyFailure(player, conditionResult);
            return false;
        }
        if (voucher.twoStep()) {
            if (confirmed) {
                // The confirm GUI already collected the second click; drop the pending entry so a
                // later redeem of the same voucher is challenged again.
                confirmations.clear(player.getUniqueId());
            } else if (confirmations.needsConfirm(player.getUniqueId(), voucher.id())) {
                if (confirmGui != null) {
                    // GUI style: confirming re-runs the redeem with the original sneaking, marked
                    // confirmed so it bypasses this gate even if the window has since lapsed.
                    confirmGui.open(player, voucher,
                        () -> scheduler.entity(player, () -> redeemHeldVoucher(player, hand, sneaking, true)),
                        () -> confirmations.clear(player.getUniqueId()));
                } else {
                    send(player, confirmMessage(player, voucher));
                }
                return false;
            }
        }
        return new VoucherPreRedeemEvent(player, voucher).callEvent();
    }

    /** The two-step confirmation prompt: the voucher's own message if set, else the locale default. */
    private String confirmMessage(Player player, Voucher voucher) {
        long seconds = confirmations.windowSeconds();
        return voucher.confirmMessage() != null
            ? messages.format(player, voucher.confirmMessage(), "voucher", voucher.id(), "seconds", seconds)
            : messages.get(player, "redeem.confirm", "voucher", voucher.id(), "seconds", seconds);
    }

    /**
     * Commits a successful voucher redemption: applies the cooldown, grants the
     * rewards, records the metric, and fires {@link VoucherRedeemEvent}. The
     * {@code uid} is the redeemed item's unique id, or {@code null} for a stackable
     * voucher. Any use limit was already reserved before the item was taken, so
     * nothing is recorded here.
     */
    private void completeVoucherRedeem(Player player, Voucher voucher, @Nullable String uid, boolean quiet) {
        if (voucher.cooldownSeconds() > 0) {
            long effective = (long) Math.ceil(
                voucher.cooldownSeconds() * cooldownTiers.multiplier(player::hasPermission));
            if (effective > 0) {
                cooldowns.apply(player.getUniqueId(), voucher.id(), effective);
            }
        }
        grantRewards(player, voucher, uid, null, quiet);
    }

    /**
     * The reward-granting core shared by item redeems and virtual claims: grant the rewards, play the
     * effects unless {@code quiet}, record the metric, and fire {@link VoucherRedeemEvent}. Applies no
     * cooldown or use limit, so a claim can reuse it without an item's redemption controls.
     */
    private void grantRewards(Player player, Voucher voucher, @Nullable String uid, @Nullable String arg,
                              boolean quiet) {
        String source = "voucher '" + voucher.id() + "'";
        grant(player, source, voucher.rewards(), voucher.randomRewards(), arg, quiet,
            voucher.definedItems(), voucher.discordWebhooks());
        if (!quiet) {
            playEffects(player, source, voucher.effects());
        }
        counters.recordVoucherRedemption();
        new VoucherRedeemEvent(player, voucher, uid).callEvent();
    }

    /**
     * Whether {@code player} may claim a virtual copy of {@code voucher} now: it must be enabled and
     * redeemable, its conditions must pass, and the pre-redeem event must not be cancelled. Replies to
     * the player on failure. A claim applies no cooldown, game mode, two-step, or use limit; the stash
     * entry is the one-time token.
     */
    public boolean canClaim(Player player, Voucher voucher) {
        if (voucher.unredeemable()) {
            send(player, messages.get(player, "redeem.unredeemable"));
            return false;
        }
        if (!voucher.enabled()) {
            send(player, messages.get(player, "redeem.disabled"));
            return false;
        }
        ConditionResult conditionResult = evaluate(voucher.conditionMaps(), player);
        if (!conditionResult.getPassed()) {
            counters.recordConditionDenial();
            replyFailure(player, conditionResult);
            return false;
        }
        return new VoucherPreRedeemEvent(player, voucher).callEvent();
    }

    /** Grants {@code amount} virtual copies of {@code voucher}, with effects and messages on the first only. */
    public void grantClaim(Player player, Voucher voucher, int amount, @Nullable String arg) {
        for (int i = 0; i < amount; i++) {
            grantRewards(player, voucher, null, arg, i > 0);
        }
        counters.recordStashClaim(amount);
    }

    /**
     * Plays the voucher's or code's optional redeem effects (sound, firework) to the player.
     * Suppressed for quiet redemptions (such as batch open) so a whole stack does not spam them.
     * A failing effect (such as an invalid sound key) is logged against {@code source} and skipped,
     * so it never aborts the grant, metric, and event around it.
     */
    private void playEffects(Player player, String source, @Nullable VoucherEffects effects) {
        if (effects == null) {
            return;
        }
        try {
            if (effects.sound() != null && !effects.sound().isBlank()) {
                Sounds.play(player, effects.sound());
            }
            if (effects.firework() != null) {
                spawnFirework(player, effects.firework());
            }
        } catch (RuntimeException ex) {
            logger.warn("Redeem effects of {} failed: {}", source, ex.getMessage());
        }
    }

    /** Spawns the redeem firework at the player, tagged so its damage is cancelled. */
    private void spawnFirework(Player player, FireworkSpec spec) {
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            FireworkEffect.Builder effect = FireworkEffect.builder()
                .with(spec.type())
                .withColor(spec.colors());
            if (!spec.fade().isEmpty()) {
                effect.withFade(spec.fade());
            }
            meta.addEffect(effect.build());
            meta.setPower(spec.power());
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(FireworkGuardListener.EFFECT_FIREWORK,
                PersistentDataType.BYTE, (byte) 1);
        });
        if (spec.power() <= 0) {
            firework.detonate();
        }
    }

    /**
     * Handles a stamped voucher the anti-dupe check refused. A duplicate is reported, then removed (or
     * marked with the warning lore) wherever it now is in the inventory; an unverifiable stamp, a lost
     * claim race, or an earlier redeem of the item still settling leaves the item untouched to retry.
     */
    private void handleRejectedItem(Player player, PlayerInventory inventory, int preferredSlot, Voucher voucher,
                                    Predicate<ItemStack> sameItem, StampStatus status) {
        if (status != StampStatus.DUPLICATE) {
            send(player, messages.get(player, "redeem.verify-failed"));
            return;
        }
        counters.recordDuplicateBlocked();
        send(player, messages.get(player, "redeem.already-redeemed"));
        notifyStaff("staff.duplicate-alert", "player", player.getName(), "voucher", voucher.id(),
            "world", player.getWorld().getName());
        int slot = locate(inventory, preferredSlot, sameItem);
        if (slot < 0) {
            return;
        }
        if (removeOnDiscovery) {
            take(inventory, slot, 1);
        } else {
            ItemStack duplicate = inventory.getItem(slot);
            applyWarningLore(duplicate);
            inventory.setItem(slot, duplicate);
        }
    }

    /** Appends the duplicate warning lore to a rejected item, once, if enabled. */
    private void applyWarningLore(ItemStack item) {
        if (!warningEnabled) {
            return;
        }
        item.editMeta(meta -> {
            if (stamp.isWarned(meta)) {
                return;
            }
            List<Component> lore = meta.lore();
            List<Component> updated = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
            updated.add(text.render(warningText).decoration(TextDecoration.ITALIC, false));
            meta.lore(updated);
            stamp.setWarned(meta);
        });
    }

    /** Attempts to redeem a typeable code by its literal input and optional argument. */
    public void redeemCode(Player player, String input, @Nullable String argument) {
        VoucherCode code = registry.findCode(input);
        if (code == null) {
            send(player, messages.get(player, "code.unknown"));
            return;
        }
        if (!code.enabled()) {
            send(player, messages.get(player, "code.disabled"));
            return;
        }
        Instant now = Instant.now();
        if (Expiry.isExpired(Expiry.resolve(code.expiry(), now), now)) {
            send(player, messages.get(player, "code.expired"));
            return;
        }
        Instant codeActiveFrom = Expiry.resolveStart(code.activeFrom());
        if (codeActiveFrom != null && now.isBefore(codeActiveFrom)) {
            send(player, messages.get(player, "code.not-yet-active"));
            return;
        }
        if (!gameModeAllowed(player)) {
            send(player, messages.get(player, "code.wrong-gamemode"));
            return;
        }
        ConditionResult conditionResult = evaluate(code.conditionMaps(), player);
        if (!conditionResult.getPassed()) {
            counters.recordConditionDenial();
            replyFailure(player, conditionResult);
            return;
        }
        if (!new VoucherCodePreRedeemEvent(player, code, argument).callEvent()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        scheduler.async(() -> {
            String denialKey;
            try {
                // One atomic reservation against both caps, so two players cannot share the last use.
                UseReservation reservation = storage.reserveUses(code.code(), playerId, 1, code.usesPerPlayer(),
                    code.hasGlobalLimit() ? code.maxUses() : VoucherStorage.UNCAPPED);
                denialKey = reservation.reserved() > 0 ? null
                    : reservation.limit() == UseLimit.PER_PLAYER ? "code.already-redeemed"
                    : "code.global-limit";
            } catch (SQLException | RuntimeException ex) {
                // A storage error (or the pool not being open yet) leaves the use unverifiable.
                denialKey = "code.verify-failed";
            }
            if (denialKey != null) {
                String denial = denialKey;
                scheduler.entity(player, () -> send(player, messages.get(player, denial)));
                return;
            }
            // The use is reserved: grant it on the player's thread, or give it back if the player is gone.
            String source = "code '" + code.code() + "'";
            handOff(player, () -> {
                grant(player, source, code.rewards(), code.randomRewards(),
                    argument, false, code.definedItems(), code.discordWebhooks());
                playEffects(player, source, code.effects());
                counters.recordCodeRedemption();
                new VoucherCodeRedeemEvent(player, code, argument).callEvent();
                send(player, messages.get(player, "code.redeemed"));
            }, () -> scheduler.asyncOrRun(() -> releaseUses(code.code(), playerId, 1)));
        });
    }

    private void grant(Player player, String source, List<RewardLine> always, List<RewardSet> random,
                       @Nullable String argument, boolean quiet,
                       java.util.Map<String, DefinedItem> definedItems,
                       java.util.Map<String, WebhookSpec> discordWebhooks) {
        List<RewardLine> granted = RewardSelection.gather(always, random, ThreadLocalRandom.current());
        for (RewardLine line : granted) {
            counters.recordRewardGranted(line.type());
        }
        rewardExecutor.execute(player, source, granted, argument, quiet, definedItems, discordWebhooks);
    }

    private ConditionResult evaluate(List<java.util.Map<String, Object>> conditionMaps, Player player) {
        if (conditionMaps.isEmpty()) {
            return ConditionResult.pass();
        }
        List<Condition> built = conditions.buildFromMaps(conditionMaps);
        return Conditions.testAll(built, ConditionContext.of(player, messages));
    }

    private void consumeOne(Player player, EquipmentSlot hand, ItemStack inHand) {
        int amount = inHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            inHand.setAmount(amount - 1);
            player.getInventory().setItem(hand, inHand);
        }
    }

    /** The raw inventory index of {@code hand}'s slot. */
    private static int handSlot(PlayerInventory inventory, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? OFF_HAND_SLOT : inventory.getHeldItemSlot();
    }

    /**
     * The inventory index of a stack that {@code matches}, checking {@code preferredSlot} first and then
     * every slot, or {@code -1} when no slot holds one.
     */
    private static int locate(PlayerInventory inventory, int preferredSlot, Predicate<ItemStack> matches) {
        if (holdsMatch(inventory, preferredSlot, matches)) {
            return preferredSlot;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != preferredSlot && holdsMatch(inventory, slot, matches)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean holdsMatch(PlayerInventory inventory, int slot, Predicate<ItemStack> matches) {
        ItemStack item = inventory.getItem(slot);
        return item != null && !item.getType().isAir() && matches.test(item);
    }

    /** Removes {@code amount} items from the stack in {@code slot}, emptying the slot when none remain. */
    private static void take(PlayerInventory inventory, int slot, int amount) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null) {
            return;
        }
        if (stack.getAmount() <= amount) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(stack.getAmount() - amount);
            inventory.setItem(slot, stack);
        }
    }

    /**
     * Redeems every item in a stack at once. Only reached for batch-open vouchers,
     * which are validated to be stackable (no per-item id) with no cooldown. With use
     * limits configured, as many uses as the limits allow are reserved first and the
     * stack is left in the hand until the reservation is back on the player's thread.
     */
    private void openWholeStack(Player player, EquipmentSlot hand, ItemStack inHand, Voucher voucher) {
        int count = inHand.getAmount();
        if (!voucher.hasUseLimits()) {
            player.getInventory().setItem(hand, null);
            redeemBatch(player, voucher, count);
            return;
        }
        InFlightGuard.Ticket ticket = inFlight.acquire(player.getUniqueId(), voucher.id());
        if (ticket == null) {
            return;
        }
        int slot = handSlot(player.getInventory(), hand);
        ItemStack original = inHand.asOne();
        UUID playerId = player.getUniqueId();
        try {
            scheduler.async(() -> {
                Reservation reservation = reserveVoucherUses(playerId, voucher, count);
                handOff(player,
                    () -> openReserved(player, slot, voucher, original, reservation, ticket),
                    () -> abandon(playerId, voucher, null, reservation.reserved(), ticket));
            });
        } catch (RuntimeException ex) {
            // The task never ran, so nothing was reserved.
            inFlight.release(ticket);
            throw ex;
        }
    }

    /**
     * The player-thread half of a limited batch open: redeems as many reserved uses as the matching stack
     * still in the redeeming hand's slot covers, taking exactly that many items, gives back any reserved
     * use left over, and replies to a shortfall. Items the limits did not cover stay in the stack.
     *
     * <p>A reserved use counts as unused until its item is taken, and the unused ones are given back (or,
     * with none, just the guard released) in a finally, so a failing reward or reply cannot leak them.
     */
    private void openReserved(Player player, int slot, Voucher voucher, ItemStack original,
                              Reservation reservation, InFlightGuard.Ticket ticket) {
        int unused = reservation.reserved();
        try {
            PlayerInventory inventory = player.getInventory();
            // Re-read: the stack may have been moved, split, or topped up since the click.
            ItemStack held = inventory.getItem(slot);
            int present = held != null && held.isSimilar(original) ? held.getAmount() : 0;
            int opened = Math.min(reservation.reserved(), present);
            if (opened > 0) {
                take(inventory, slot, opened);
                unused -= opened;
                redeemBatch(player, voucher, opened);
            }
            if (reservation.denialKey() != null) {
                send(player, messages.get(player, reservation.denialKey()));
            } else if (opened == 0) {
                send(player, messages.get(player, "redeem.verify-failed"));
            }
        } finally {
            abandon(player.getUniqueId(), voucher, null, unused, ticket);
        }
    }

    private void redeemBatch(Player player, Voucher voucher, int count) {
        for (int i = 0; i < count; i++) {
            completeVoucherRedeem(player, voucher, null, batchQuiet);
        }
        send(player, messages.get(player, "redeem.opened", "count", count, "voucher", voucher.id()));
    }

    private void notifyStaff(String key, Object... placeholders) {
        if (!notifyEnabled) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("provouchers.notify")) {
                online.sendMessage(text.render(messages.get(online, key, placeholders), online));
            }
        }
    }

    private boolean ownsVoucher(Player player, ItemMeta meta) {
        if (player.hasPermission("provouchers.bypass.owner")) {
            return true;
        }
        String owner = stamp.owner(meta);
        return owner == null || owner.equals(player.getUniqueId().toString());
    }

    private static boolean gameModeAllowed(Player player) {
        if (player.hasPermission("provouchers.bypass.gamemode")) {
            return true;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    @Nullable
    private String readUid(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : stamp.uid(meta);
    }

    private void replyFailure(Player player, ConditionResult result) {
        Component message = result.getMessage();
        if (message != null) {
            player.sendMessage(message);
        } else {
            send(player, messages.get(player, "redeem.requirements-not-met"));
        }
    }

    private void send(Player player, String miniMessage) {
        text.send(player, miniMessage);
    }
}
