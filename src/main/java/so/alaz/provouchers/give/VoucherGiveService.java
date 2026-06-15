package so.alaz.provouchers.give;

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

/**
 * Builds and hands out voucher items, the single give path shared by the command, the public API
 * service, and the preview GUI. Overflow that does not fit the inventory is the one place that policy
 * lives: by default it drops at the player's feet, but when {@link #overflowToStash(StashService)} is
 * configured it is queued in the player's Stash as virtual vouchers instead, so a reward is never lost.
 */
public final class VoucherGiveService {

    private final VoucherItemFactory factory;
    private final Scheduler scheduler;
    @Nullable private StashService overflowStash;

    public VoucherGiveService(VoucherItemFactory factory, Scheduler scheduler) {
        this.factory = factory;
        this.scheduler = scheduler;
    }

    /** Routes give overflow into the Stash as virtual vouchers instead of dropping it on the ground. */
    public void overflowToStash(StashService stash) {
        this.overflowStash = stash;
    }

    /** Gives {@code amount} of {@code voucher} to {@code target}, stashing or dropping any overflow. */
    public void give(Player target, Voucher voucher, int amount) {
        factory.createItems(voucher, amount, target).thenAccept(items ->
            scheduler.entity(target, () -> deliver(target, voucher, items)));
    }

    private void deliver(Player target, Voucher voucher, List<ItemStack> items) {
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(items.toArray(ItemStack[]::new));
        if (leftovers.isEmpty()) {
            return;
        }
        if (overflowStash != null) {
            int count = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            overflowStash.stash(target.getUniqueId(), voucher.id(), count, null, StashSource.OVERFLOW);
        } else {
            leftovers.values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        }
    }
}
