package so.alaz.provouchers.give;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.stash.StashService;
import so.alaz.provouchers.stash.StashSource;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.platform.PendingSettlements;
import so.alaz.provouchers.platform.Scheduler;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Builds and hands out voucher items for the command, the public API service, and the preview GUI.
 * Overflow that does not fit the inventory drops at the player's feet, or is queued in the Stash as
 * virtual vouchers when {@link #overflowPolicy} routes it there.
 *
 * <p>A target who logs out before the items reach them gets the whole amount queued in their Stash
 * whenever the Stash is enabled, whatever the overflow policy. With the Stash disabled, the undelivered
 * amount is logged. Paper runs neither callback of a hop still scheduled when the plugin disables, so
 * each hop is tracked until it starts, and {@link #settleForShutdown} treats a give whose hop never ran
 * the same way.
 */
public final class VoucherGiveService {

    private final VoucherItemFactory factory;
    private final Scheduler scheduler;
    private final ComponentLogger logger;
    /** Gives whose items are built but have neither been delivered nor handled as undelivered yet. */
    private final PendingSettlements deliveries = new PendingSettlements();
    @Nullable private StashService stashService;
    private BooleanSupplier overflowToStash = () -> false;

    public VoucherGiveService(VoucherItemFactory factory, Scheduler scheduler, ComponentLogger logger) {
        this.factory = factory;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /**
     * Configures overflow: {@code stashService} receives the leftovers when {@code overflowToStash}
     * reports {@code true} at delivery time. The supplier is read on every give.
     */
    public void overflowPolicy(StashService stashService, BooleanSupplier overflowToStash) {
        this.stashService = stashService;
        this.overflowToStash = overflowToStash;
    }

    /**
     * Gives {@code amount} of {@code voucher} to {@code target}, stashing or dropping any overflow. If the
     * target logs out or the plugin disables first, the whole amount is stashed or logged; a give that
     * fails is logged too.
     */
    public void give(Player target, Voucher voucher, int amount) {
        factory.createItems(voucher, amount, target)
            .thenAccept(items -> {
                // Exactly one of the two callbacks or the shutdown settlement handles the give.
                PendingSettlements.Entry pending = deliveries.track(
                    () -> undelivered(target, voucher, items, "the plugin disabled before delivery"));
                scheduler.entity(target,
                    () -> {
                        if (pending.start()) {
                            deliver(target, voucher, items);
                        }
                    },
                    () -> {
                        if (pending.start()) {
                            undelivered(target, voucher, items,
                                "they logged out or the plugin disabled before delivery");
                        }
                    });
            })
            .exceptionally(error -> {
                logger.warn("Failed to give {}x voucher '{}' to {}",
                    amount, voucher.id(), target.getName(), error);
                return null;
            });
    }

    /**
     * Settles every give whose items were built but whose hop to the target never ran. Call it from
     * {@code onDisable}, once the plugin is no longer enabled and before the Stash settles and storage
     * closes. Each give is handled on the calling thread as if its target had logged out: queued in the
     * Stash when that is enabled, and otherwise logged.
     */
    public void settleForShutdown() {
        int settled = deliveries.settleAll(
            ex -> logger.warn("Could not settle an undelivered give during shutdown", ex));
        if (settled > 0) {
            logger.info("Settled {} give(s) that had not reached their target during shutdown.", settled);
        }
    }

    private void deliver(Player target, Voucher voucher, List<ItemStack> items) {
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(items.toArray(ItemStack[]::new));
        if (leftovers.isEmpty()) {
            return;
        }
        if (stashService != null && overflowToStash.getAsBoolean()) {
            int count = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            stashService.stash(target.getUniqueId(), voucher.id(), count, null, StashSource.OVERFLOW);
        } else {
            leftovers.values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        }
    }

    /**
     * Runs instead of {@link #deliver} when the target logged out or the plugin disabled first. It may run
     * off the region thread, so it touches neither the world nor an inventory: the whole amount is queued
     * in the Stash when that is enabled, and otherwise logged with {@code reason}.
     */
    private void undelivered(Player target, Voucher voucher, List<ItemStack> items, String reason) {
        int count = items.stream().mapToInt(ItemStack::getAmount).sum();
        if (count < 1) {
            return;
        }
        if (stashService != null) {
            stashService.stash(target.getUniqueId(), voucher.id(), count, null, StashSource.OFFLINE_GIVE);
        } else {
            logger.warn("Could not give {}x voucher '{}' to {}: {} and the Stash is disabled",
                count, voucher.id(), target.getName(), reason);
        }
    }
}
