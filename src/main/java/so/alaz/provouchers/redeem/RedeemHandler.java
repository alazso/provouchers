package so.alaz.provouchers.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
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
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.provouchers.util.Expiry;
import so.alaz.provouchers.condition.Condition;
import so.alaz.provouchers.condition.ConditionContext;
import so.alaz.provouchers.condition.ConditionRegistry;
import so.alaz.provouchers.condition.ConditionResult;
import so.alaz.provouchers.condition.Conditions;
import so.alaz.provouchers.cooldown.CooldownService;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The redemption pipeline shared by item vouchers and codes. Synchronous checks
 * (existence, expiry, game mode, cooldown, conditions) run on the caller's thread;
 * persistent checks (duplicate stamps, code-use limits) run on the async
 * scheduler, then control hops back to the player's region to grant rewards.
 */
public final class RedeemHandler {

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
    private final boolean removeOnDiscovery;
    private final boolean warningEnabled;
    private final String warningText;
    private final boolean notifyEnabled;
    private final boolean batchQuiet;
    private final ConfirmationTracker confirmations;

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
        if (!voucherPreChecks(player, voucher)) {
            return;
        }

        if (sneaking && voucher.batchOpen()) {
            openWholeStack(player, hand, inHand, voucher);
            return;
        }

        ItemStack consumed = consumeOne(player, hand, inHand);
        String uid = readUid(consumed);
        if (uid == null) {
            // Stackable voucher: no anti-dupe tracking, commit on the current thread.
            completeVoucherRedeem(player, voucher, null, false);
            return;
        }
        scheduler.async(() -> {
            StampStatus status = dupeDetector.check(uid);
            boolean allowed = status == StampStatus.VALID
                && dupeDetector.claim(uid, player.getUniqueId());
            boolean finalAllowed = allowed;
            StampStatus finalStatus = status;
            scheduler.entity(player, () -> {
                if (finalAllowed) {
                    completeVoucherRedeem(player, voucher, uid, false);
                } else {
                    handleRejectedItem(player, voucher, consumed, finalStatus);
                }
            });
        });
    }

    /**
     * The source-agnostic gates a voucher redemption must pass before it commits:
     * game mode, cooldown, conditions, and the cancellable pre-redeem event. Replies
     * to the player on failure. Reused by the item path and (later) virtual vouchers.
     */
    private boolean voucherPreChecks(Player player, Voucher voucher) {
        if (!gameModeAllowed(player)) {
            send(player, messages.get(player, "redeem.wrong-gamemode"));
            return false;
        }
        if (!player.hasPermission("provouchers.bypass.cooldown")
            && cooldowns.isOnCooldown(player.getUniqueId(), voucher.id())) {
            long seconds = cooldowns.remaining(player.getUniqueId(), voucher.id()).toSeconds();
            send(player, messages.get(player, "redeem.cooldown", "seconds", Math.max(1, seconds)));
            return false;
        }
        ConditionResult conditionResult = evaluate(voucher.conditionMaps(), player);
        if (!conditionResult.getPassed()) {
            counters.recordConditionDenial();
            replyFailure(player, conditionResult);
            return false;
        }
        if (voucher.twoStep() && confirmations.needsConfirm(player.getUniqueId(), voucher.id())) {
            send(player, confirmMessage(player, voucher));
            return false;
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
     * voucher. Reused by the item path and (later) virtual vouchers and batch open.
     */
    private void completeVoucherRedeem(Player player, Voucher voucher, @Nullable String uid, boolean quiet) {
        if (voucher.cooldownSeconds() > 0) {
            cooldowns.apply(player.getUniqueId(), voucher.id(), voucher.cooldownSeconds());
        }
        grant(player, "voucher '" + voucher.id() + "'", voucher.rewards(), voucher.randomRewards(), null, quiet);
        counters.recordVoucherRedemption();
        new VoucherRedeemEvent(player, voucher, uid).callEvent();
    }

    /** Handles a physical voucher that failed the duplicate check: warns, notifies, and refunds. */
    private void handleRejectedItem(Player player, Voucher voucher, ItemStack consumed, StampStatus status) {
        if (status == StampStatus.DUPLICATE) {
            counters.recordDuplicateBlocked();
            send(player, messages.get(player, "redeem.already-redeemed"));
            notifyStaff("staff.duplicate-alert", "player", player.getName(), "voucher", voucher.id(),
                "world", player.getWorld().getName());
            if (!removeOnDiscovery) {
                applyWarningLore(consumed);
                refund(player, consumed);
            }
        } else {
            send(player, messages.get(player, "redeem.verify-failed"));
            refund(player, consumed);
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
        Instant now = Instant.now();
        if (Expiry.isExpired(Expiry.resolve(code.expiry(), now), now)) {
            send(player, messages.get(player, "code.expired"));
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
        scheduler.async(() -> {
            boolean allowed = true;
            String denyMessage = null;
            try {
                int mine = storage.codeUsesByPlayer(code.code(), player.getUniqueId());
                if (mine >= code.usesPerPlayer()) {
                    allowed = false;
                    denyMessage = messages.get(player, "code.already-redeemed");
                } else if (code.hasGlobalLimit() && storage.codeUsesTotal(code.code()) >= code.maxUses()) {
                    allowed = false;
                    denyMessage = messages.get(player, "code.global-limit");
                } else {
                    storage.incrementCodeUse(code.code(), player.getUniqueId());
                }
            } catch (SQLException | RuntimeException ex) {
                // A storage error (or the pool not being open yet) leaves the use unverifiable.
                allowed = false;
                denyMessage = messages.get(player, "code.verify-failed");
            }
            boolean finalAllowed = allowed;
            String finalDeny = denyMessage;
            scheduler.entity(player, () -> {
                if (!finalAllowed) {
                    send(player, finalDeny);
                    return;
                }
                grant(player, "code '" + code.code() + "'", code.rewards(), code.randomRewards(),
                    argument, false);
                counters.recordCodeRedemption();
                new VoucherCodeRedeemEvent(player, code, argument).callEvent();
                send(player, messages.get(player, "code.redeemed"));
            });
        });
    }

    private void grant(Player player, String source, List<RewardLine> always, List<RewardSet> random,
                       @Nullable String argument, boolean quiet) {
        List<RewardLine> granted = RewardSelection.gather(always, random, ThreadLocalRandom.current());
        for (RewardLine line : granted) {
            counters.recordRewardGranted(line.type());
        }
        rewardExecutor.execute(player, source, granted, argument, quiet);
    }

    private ConditionResult evaluate(List<java.util.Map<String, Object>> conditionMaps, Player player) {
        if (conditionMaps.isEmpty()) {
            return ConditionResult.pass();
        }
        List<Condition> built = conditions.buildFromMaps(conditionMaps);
        return Conditions.testAll(built, ConditionContext.of(player, messages));
    }

    private ItemStack consumeOne(Player player, EquipmentSlot hand, ItemStack inHand) {
        ItemStack single = inHand.asOne();
        int amount = inHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            inHand.setAmount(amount - 1);
            player.getInventory().setItem(hand, inHand);
        }
        return single;
    }

    /**
     * Redeems every item in a stack at once. Only reached for batch-open vouchers,
     * which are validated to be stackable (no per-item id) with no cooldown.
     */
    private void openWholeStack(Player player, EquipmentSlot hand, ItemStack inHand, Voucher voucher) {
        int count = inHand.getAmount();
        player.getInventory().setItem(hand, null);
        for (int i = 0; i < count; i++) {
            completeVoucherRedeem(player, voucher, null, batchQuiet);
        }
        send(player, messages.get(player, "redeem.opened", "count", count, "voucher", voucher.id()));
    }

    private void refund(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
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
        player.sendMessage(text.render(miniMessage, player));
    }
}
