package so.alaz.provouchers.redeem;

import net.kyori.adventure.text.Component;
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
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSelection;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.provouchers.util.Expiry;
import so.alaz.strata.api.condition.Condition;
import so.alaz.strata.api.condition.ConditionContext;
import so.alaz.strata.api.condition.ConditionRegistry;
import so.alaz.strata.api.condition.ConditionResult;
import so.alaz.strata.api.condition.Conditions;
import so.alaz.strata.api.cooldown.CooldownManager;
import so.alaz.strata.api.scheduler.PlatformScheduler;
import so.alaz.strata.api.text.TextRenderer;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The redemption pipeline shared by item vouchers and codes. Synchronous checks
 * (existence, expiry, game mode, cooldown, conditions) run on the caller's thread;
 * persistent checks (duplicate stamps, code-use limits) run on Strata's async
 * scheduler, then control hops back to the player's region to grant rewards.
 */
public final class RedeemHandler {

    private final VoucherRegistry registry;
    private final VoucherStamp stamp;
    private final DupeDetector dupeDetector;
    private final VoucherStorage storage;
    private final RewardExecutor rewardExecutor;
    private final PlatformScheduler scheduler;
    private final TextRenderer text;
    private final CooldownManager cooldowns;
    private final ConditionRegistry conditions;
    private final boolean removeOnDiscovery;

    public RedeemHandler(
        VoucherRegistry registry,
        VoucherStamp stamp,
        DupeDetector dupeDetector,
        VoucherStorage storage,
        RewardExecutor rewardExecutor,
        PlatformScheduler scheduler,
        TextRenderer text,
        CooldownManager cooldowns,
        ConditionRegistry conditions,
        boolean removeOnDiscovery
    ) {
        this.registry = registry;
        this.stamp = stamp;
        this.dupeDetector = dupeDetector;
        this.storage = storage;
        this.rewardExecutor = rewardExecutor;
        this.scheduler = scheduler;
        this.text = text;
        this.cooldowns = cooldowns;
        this.conditions = conditions;
        this.removeOnDiscovery = removeOnDiscovery;
    }

    /** Attempts to redeem the voucher item held in {@code hand}. */
    public void redeemHeldVoucher(Player player, EquipmentSlot hand) {
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
            send(player, "<red>This voucher is no longer configured.");
            return;
        }
        if (voucher.unredeemable()) {
            send(player, "<red>This voucher cannot be redeemed.");
            return;
        }
        Instant now = Instant.now();
        if (Expiry.isExpired(Expiry.resolve(voucher.expiry(), now), now)) {
            send(player, "<red>This voucher has expired.");
            return;
        }
        if (!gameModeAllowed(player)) {
            send(player, "<red>You cannot redeem vouchers in this game mode.");
            return;
        }
        String cooldownKey = cooldownKey(player, voucher.id());
        if (onCooldown(player, cooldownKey)) {
            long seconds = cooldowns.remaining(cooldownKey).toSeconds();
            send(player, "<red>You must wait " + Math.max(1, seconds) + "s before redeeming this again.");
            return;
        }
        ConditionResult conditionResult = evaluate(voucher.conditionMaps(), player);
        if (!conditionResult.getPassed()) {
            replyFailure(player, conditionResult);
            return;
        }

        ItemStack consumed = consumeOne(player, hand, inHand);
        String batchId = readBatch(consumed);
        String nonce = readNonce(consumed);

        scheduler.async(() -> {
            boolean allowed;
            StampStatus status;
            if (batchId == null || nonce == null) {
                status = StampStatus.VALID;
                allowed = true;
            } else {
                status = dupeDetector.check(batchId, nonce);
                allowed = status == StampStatus.VALID
                    && dupeDetector.claim(batchId, nonce, player.getUniqueId());
            }
            boolean finalAllowed = allowed;
            StampStatus finalStatus = status;
            scheduler.entity(player, () ->
                finishItemRedeem(player, voucher, consumed, finalAllowed, finalStatus, cooldownKey));
        });
    }

    private void finishItemRedeem(Player player, Voucher voucher, ItemStack consumed, boolean allowed,
                                  StampStatus status, String cooldownKey) {
        if (!allowed) {
            if (status == StampStatus.DUPLICATE) {
                send(player, "<red>This voucher has already been redeemed.");
                notifyStaff(player.getName() + " tried to redeem a duplicate '" + voucher.id() + "'.");
                if (!removeOnDiscovery) {
                    refund(player, consumed);
                }
            } else {
                send(player, "<red>Could not verify this voucher. Please try again.");
                refund(player, consumed);
            }
            return;
        }
        if (voucher.cooldownSeconds() > 0) {
            cooldowns.set(cooldownKey, Duration.ofSeconds(voucher.cooldownSeconds()));
        }
        grant(player, voucher.rewards(), voucher.randomRewards(), null);
    }

    /** Attempts to redeem a typeable code by its literal input and optional argument. */
    public void redeemCode(Player player, String input, @Nullable String argument) {
        VoucherCode code = registry.findCode(input);
        if (code == null) {
            send(player, "<red>Unknown code.");
            return;
        }
        Instant now = Instant.now();
        if (Expiry.isExpired(Expiry.resolve(code.expiry(), now), now)) {
            send(player, "<red>This code has expired.");
            return;
        }
        if (!gameModeAllowed(player)) {
            send(player, "<red>You cannot redeem codes in this game mode.");
            return;
        }
        ConditionResult conditionResult = evaluate(code.conditionMaps(), player);
        if (!conditionResult.getPassed()) {
            replyFailure(player, conditionResult);
            return;
        }
        scheduler.async(() -> {
            boolean allowed = true;
            String denyMessage = null;
            try {
                int mine = storage.codeUsesByPlayer(code.code(), player.getUniqueId());
                if (mine >= code.usesPerPlayer()) {
                    allowed = false;
                    denyMessage = "<red>You have already redeemed this code.";
                } else if (code.hasGlobalLimit() && storage.codeUsesTotal(code.code()) >= code.maxUses()) {
                    allowed = false;
                    denyMessage = "<red>This code has reached its global use limit.";
                } else {
                    storage.incrementCodeUse(code.code(), player.getUniqueId());
                }
            } catch (SQLException ex) {
                allowed = false;
                denyMessage = "<red>Could not verify the code. Please try again.";
            }
            boolean finalAllowed = allowed;
            String finalDeny = denyMessage;
            scheduler.entity(player, () -> {
                if (!finalAllowed) {
                    send(player, finalDeny);
                    return;
                }
                grant(player, code.rewards(), code.randomRewards(), argument);
                send(player, "<green>Code redeemed.");
            });
        });
    }

    private void grant(Player player, List<RewardLine> always, List<RewardSet> random,
                       @Nullable String argument) {
        List<RewardLine> granted = RewardSelection.gather(always, random, ThreadLocalRandom.current());
        rewardExecutor.execute(player, granted, argument);
    }

    private ConditionResult evaluate(List<java.util.Map<String, Object>> conditionMaps, Player player) {
        if (conditionMaps.isEmpty()) {
            return ConditionResult.pass();
        }
        List<Condition> built = conditions.buildFromMaps(conditionMaps);
        return Conditions.testAll(built, ConditionContext.of(player));
    }

    private ItemStack consumeOne(Player player, EquipmentSlot hand, ItemStack inHand) {
        ItemStack single = inHand.asOne();
        single.editMeta(meta -> {
            if (!stamp.hasNonce(meta)) {
                stamp.setNonce(meta, VoucherStamp.newNonce());
            }
        });
        int amount = inHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            inHand.setAmount(amount - 1);
            player.getInventory().setItem(hand, inHand);
        }
        return single;
    }

    private void refund(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void notifyStaff(String message) {
        Component rendered = text.render("<gold>[ProVouchers] <yellow>" + message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("provouchers.notify")) {
                online.sendMessage(rendered);
            }
        }
    }

    private boolean onCooldown(Player player, String key) {
        return !player.hasPermission("provouchers.bypass.cooldown") && cooldowns.isOnCooldown(key);
    }

    private static boolean gameModeAllowed(Player player) {
        if (player.hasPermission("provouchers.bypass.gamemode")) {
            return true;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    @Nullable
    private String readBatch(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : stamp.batchId(meta);
    }

    @Nullable
    private String readNonce(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : stamp.nonce(meta);
    }

    private static String cooldownKey(Player player, String voucherId) {
        return player.getUniqueId() + ":" + voucherId;
    }

    private void replyFailure(Player player, ConditionResult result) {
        Component message = result.getMessage();
        if (message != null) {
            player.sendMessage(message);
        } else {
            send(player, "<red>You do not meet the requirements to redeem this.");
        }
    }

    private void send(Player player, String miniMessage) {
        player.sendMessage(text.render(miniMessage, player));
    }
}
