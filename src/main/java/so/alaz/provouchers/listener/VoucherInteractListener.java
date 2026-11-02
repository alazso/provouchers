package so.alaz.provouchers.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.function.BooleanSupplier;
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
 * a GUI, and using a voucher on an entity is blocked.
 */
public final class VoucherInteractListener implements Listener {

    private final VoucherStamp stamp;
    private final RedeemHandler handler;
    private final VoucherRegistry registry;
    private final RewardPreviewGui previewGui;
    private final BooleanSupplier leftClickPreview;
    private final BooleanSupplier blockEntityUse;

    public VoucherInteractListener(VoucherStamp stamp, RedeemHandler handler, VoucherRegistry registry,
                                   RewardPreviewGui previewGui, BooleanSupplier leftClickPreview,
                                   BooleanSupplier blockEntityUse) {
        this.stamp = stamp;
        this.handler = handler;
        this.registry = registry;
        this.previewGui = previewGui;
        this.leftClickPreview = leftClickPreview;
        this.blockEntityUse = blockEntityUse;
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
        } else if (leftClickPreview.getAsBoolean()
            && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            Voucher voucher = registry.getVoucher(stamp.voucherId(meta)).orElse(null);
            if (voucher != null) {
                previewGui.open(event.getPlayer(), voucher);
            }
        }
    }

    /**
     * Keeps a held voucher from acting on an entity, where the game would spend it with no reward (a
     * name tag names the target, a lead leashes it, food breeds it) or take it (an item frame or armor
     * stand holds it, an allay carries it). {@link PlayerInteractAtEntityEvent}, the event the server
     * fires for an entity click, has no handler list of its own, so this listener receives it.
     */
    @EventHandler
    public void onUseOnEntity(PlayerInteractEntityEvent event) {
        if (!blocksUseOn(event.getRightClicked(), blockEntityUse.getAsBoolean())) {
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

    /**
     * Whether a voucher is kept from acting on {@code target}. Item frames are always covered. Other
     * entities follow {@code redeem.block-entity-use}, because cancelling also stops the entity's own
     * interaction while a voucher is held: trading, mounting, clicking a mob-type NPC. Players are never
     * covered: no item acts on a player, and a player-type NPC has to stay clickable.
     */
    static boolean blocksUseOn(Entity target, boolean blockEntityUse) {
        if (target instanceof Player) {
            return false;
        }
        return target instanceof ItemFrame || blockEntityUse;
    }
}
