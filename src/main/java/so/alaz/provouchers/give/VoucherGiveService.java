package so.alaz.provouchers.give;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.platform.Scheduler;

import java.util.List;

/**
 * Builds and hands out voucher items, the single give path shared by the command,
 * the public API service, and (later) the preview GUI and Vault. Overflow that does
 * not fit is dropped at the player's feet; this is the one place that policy lives,
 * so it can later be repointed at the deposit box.
 */
public final class VoucherGiveService {

    private final VoucherItemFactory factory;
    private final Scheduler scheduler;

    public VoucherGiveService(VoucherItemFactory factory, Scheduler scheduler) {
        this.factory = factory;
        this.scheduler = scheduler;
    }

    /** Gives {@code amount} of {@code voucher} to {@code target}, dropping any overflow. */
    public void give(Player target, Voucher voucher, int amount) {
        factory.createItems(voucher, amount, target).thenAccept(items ->
            scheduler.entity(target, () -> deliver(target, items)));
    }

    private static void deliver(Player target, List<ItemStack> items) {
        target.getInventory().addItem(items.toArray(ItemStack[]::new)).values()
            .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
    }
}
