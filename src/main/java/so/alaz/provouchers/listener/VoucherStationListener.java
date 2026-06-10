package so.alaz.provouchers.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import so.alaz.provouchers.antidupe.VoucherStamp;

import java.util.Set;

/**
 * Stops a voucher item from being used as an ingredient in a station that consumes or
 * transforms items, closing off "craft/transform the voucher" as a way to dupe or destroy
 * a stamped voucher. Crafting (the player 2x2 and a crafting table) is handled by clearing
 * the result when a voucher is in the matrix; the other stations are handled by blocking a
 * voucher from being placed into their slots.
 */
public final class VoucherStationListener implements Listener {

    /** Stations with a dedicated input inventory; crafting is handled separately via the craft event. */
    private static final Set<InventoryType> BLOCKED_STATIONS = Set.of(
        InventoryType.ANVIL, InventoryType.GRINDSTONE, InventoryType.SMITHING,
        InventoryType.LOOM, InventoryType.CARTOGRAPHY, InventoryType.STONECUTTER,
        InventoryType.BREWING);

    private final VoucherStamp stamp;

    public VoucherStationListener(VoucherStamp stamp) {
        this.stamp = stamp;
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isVoucher(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!BLOCKED_STATIONS.contains(top.getType())) {
            return;
        }
        boolean placeIntoStation = event.getRawSlot() < top.getSize() && isVoucher(event.getCursor());
        boolean shiftIntoStation = event.isShiftClick()
            && event.getClickedInventory() != null
            && event.getClickedInventory().getType() == InventoryType.PLAYER
            && isVoucher(event.getCurrentItem());
        if (placeIntoStation || shiftIntoStation) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (!BLOCKED_STATIONS.contains(top.getType()) || !isVoucher(event.getOldCursor())) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    private boolean isVoucher(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && stamp.isVoucher(meta);
    }
}
