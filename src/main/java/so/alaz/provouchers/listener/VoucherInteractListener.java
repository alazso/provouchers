package so.alaz.provouchers.listener;

import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.redeem.RedeemHandler;

/**
 * Turns a right-click with a voucher item into a redemption, and blocks placing
 * vouchers into item frames (a common way to detach an item from its owner while
 * keeping a copy).
 */
public final class VoucherInteractListener implements Listener {

    private final VoucherStamp stamp;
    private final RedeemHandler handler;

    public VoucherInteractListener(VoucherStamp stamp, RedeemHandler handler) {
        this.stamp = stamp;
        this.handler = handler;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !stamp.isVoucher(meta)) {
            return;
        }
        event.setCancelled(true);
        handler.redeemHeldVoucher(event.getPlayer(), event.getHand());
    }

    @EventHandler
    public void onPlaceInItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && stamp.isVoucher(meta)) {
            event.setCancelled(true);
        }
    }
}
