package so.alaz.provouchers.listener;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.voucher.SoulboundSpec;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

/**
 * Enforces a voucher's {@code soulbound} restrictions: blocks dropping, blocks moving
 * the item into other inventories (containers, hoppers, item frames), and stamps an
 * unowned soulbound voucher with whoever picks it up. Each restriction is a per-voucher
 * toggle on {@link SoulboundSpec}.
 */
public final class SoulboundListener implements Listener {

    private final VoucherStamp stamp;
    private final VoucherRegistry registry;
    private final Text text;
    private final Messages messages;

    public SoulboundListener(VoucherStamp stamp, VoucherRegistry registry, Text text, Messages messages) {
        this.stamp = stamp;
        this.registry = registry;
        this.text = text;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        SoulboundSpec spec = soulbound(event.getItemDrop().getItemStack());
        if (spec != null && spec.blockDrop()) {
            event.setCancelled(true);
            warn(event.getPlayer(), "soulbound.no-drop");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return; // own inventory view; the item is not leaving the player
        }
        boolean intoContainer =
            // shift-click out of the player's inventory
            (event.isShiftClick() && event.getClickedInventory() == event.getView().getBottomInventory()
                && blocked(event.getCurrentItem()))
            // placing the cursor item into the open container
            || (event.getClickedInventory() == event.getView().getTopInventory()
                && (blocked(event.getCursor())
                    // number-key swap pulling a hotbar item into the container
                    || (event.getHotbarButton() >= 0
                        && blocked(player.getInventory().getItem(event.getHotbarButton())))));
        if (intoContainer) {
            event.setCancelled(true);
            warn(player, "soulbound.no-container");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !blocked(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
            && event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
            warn(player, "soulbound.no-container");
        }
    }

    /** A hopper or other block inventory collecting a dropped soulbound voucher. */
    @EventHandler(ignoreCancelled = true)
    public void onContainerPickup(InventoryPickupItemEvent event) {
        SoulboundSpec spec = soulbound(event.getItem().getItemStack());
        if (spec != null && spec.blockContainers()) {
            event.setCancelled(true);
        }
    }

    /** Placing a soulbound voucher into an item frame. */
    @EventHandler(ignoreCancelled = true)
    public void onFramePlace(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        SoulboundSpec spec = held == null ? null : soulbound(held);
        if (spec != null && spec.blockContainers()) {
            event.setCancelled(true);
            warn(event.getPlayer(), "soulbound.no-container");
        }
    }

    /** Binds an unowned soulbound voucher to the player who picks it up. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        SoulboundSpec spec = soulbound(item);
        if (spec == null || !spec.bindOnPickup()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && stamp.owner(meta) == null) {
            stamp.setOwner(meta, player.getUniqueId());
            item.setItemMeta(meta);
            event.getItem().setItemStack(item);
        }
    }

    private boolean blocked(@Nullable ItemStack item) {
        SoulboundSpec spec = item == null ? null : soulbound(item);
        return spec != null && spec.blockContainers();
    }

    /** The item's soulbound spec, or {@code null} when it is not a soulbound voucher. */
    @Nullable
    private SoulboundSpec soulbound(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !stamp.isVoucher(meta)) {
            return null;
        }
        return registry.getVoucher(stamp.voucherId(meta)).map(Voucher::soulbound).orElse(null);
    }

    private void warn(Player player, String key) {
        player.sendActionBar(text.render(messages.get(player, key), player));
    }
}
