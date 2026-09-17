package so.alaz.provouchers.give;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.stash.StashService;
import so.alaz.provouchers.stash.StashSource;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.platform.Scheduler;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Builds and hands out voucher items, the single give path shared by the command, the public API
 * service, and the preview GUI. Overflow that does not fit the inventory is the one place that policy
 * lives: by default it drops at the player's feet, but when {@link #overflowPolicy} routes it to the
 * Stash it is queued as virtual vouchers instead, so a reward is never lost. The policy is read live,
 * so {@code /voucher reload} can switch it without a restart.
 *
 * <p>A target who logs out before the items reach them gets the whole amount queued in their Stash
 * whenever the Stash is enabled, whatever the overflow policy, since nothing can be dropped for a player
 * who is gone. With the Stash disabled, the undelivered amount is logged.
 */
public final class VoucherGiveService {

    private final VoucherItemFactory factory;
    private final Scheduler scheduler;
    private final ComponentLogger logger;
    @Nullable private StashService stashService;
    private BooleanSupplier overflowToStash = () -> false;

    public VoucherGiveService(VoucherItemFactory factory, Scheduler scheduler, ComponentLogger logger) {
        this.factory = factory;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /**
     * Configures overflow: {@code stashService} receives the leftovers when {@code overflowToStash}
     * reports {@code true} at delivery time. The supplier is read on every give, so a config change
     * picked up by {@code /voucher reload} takes effect without a restart.
     */
    public void overflowPolicy(StashService stashService, BooleanSupplier overflowToStash) {
        this.stashService = stashService;
        this.overflowToStash = overflowToStash;
    }

    /**
     * Gives {@code amount} of {@code voucher} to {@code target}, stashing or dropping any overflow. If the
     * target logs out first, the whole amount is stashed or logged; a give that fails is logged too.
     */
    public void give(Player target, Voucher voucher, int amount) {
        factory.createItems(voucher, amount, target)
            .thenAccept(items -> scheduler.entity(target,
                () -> deliver(target, voucher, items),
                () -> undelivered(target, voucher, items)))
            .exceptionally(error -> {
                logger.warn("Failed to give {}x voucher '{}' to {}",
                    amount, voucher.id(), target.getName(), error);
                return null;
            });
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
     * Runs instead of {@link #deliver} when the target logged out first. It may run off the region thread,
     * so it touches neither the world nor an inventory: the whole amount is queued in the Stash when that
     * is enabled, and is otherwise logged as not delivered.
     */
    private void undelivered(Player target, Voucher voucher, List<ItemStack> items) {
        int count = items.stream().mapToInt(ItemStack::getAmount).sum();
        if (count < 1) {
            return;
        }
        if (stashService != null) {
            stashService.stash(target.getUniqueId(), voucher.id(), count, null, StashSource.OFFLINE_GIVE);
        } else {
            logger.warn("Could not give {}x voucher '{}' to {}: they logged out before delivery and the "
                + "Stash is disabled", count, voucher.id(), target.getName());
        }
    }
}
