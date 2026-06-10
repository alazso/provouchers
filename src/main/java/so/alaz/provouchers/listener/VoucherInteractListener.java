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
import so.alaz.provouchers.gui.RewardPreviewGui;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

/**
 * Turns interactions with a voucher item into actions: a right-click redeems it (a
 * sneak-right-click batch-opens a stack), an optional left-click previews its rewards in
 * a GUI, and placing a voucher into an item frame is blocked (a common way to detach an
 * item from its owner while keeping a copy).
 */
public final class VoucherInteractListener implements Listener {

    private final VoucherStamp stamp;
    private final RedeemHandler handler;
    private final VoucherRegistry registry;
    private final RewardPreviewGui previewGui;
    private final boolean leftClickPreview;

    public VoucherInteractListener(VoucherStamp stamp, RedeemHandler handler, VoucherRegistry registry,
                                   RewardPreviewGui previewGui, boolean leftClickPreview) {
        this.stamp = stamp;
        this.handler = handler;
        this.registry = registry;
        this.previewGui = previewGui;
        this.leftClickPreview = leftClickPreview;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) {
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
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            handler.redeemHeldVoucher(event.getPlayer(), event.getHand(), event.getPlayer().isSneaking());
        } else if (leftClickPreview
            && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            Voucher voucher = registry.getVoucher(stamp.voucherId(meta)).orElse(null);
            if (voucher != null) {
                previewGui.open(event.getPlayer(), voucher);
            }
        }
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
