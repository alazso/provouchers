package so.alaz.provouchers.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.antidupe.VoucherStamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stops a voucher item from being used as an ingredient in a station that consumes or
 * transforms items. Crafting (the player 2x2 and a crafting table) is handled by clearing
 * the result when a voucher is in the matrix; the other stations are handled by blocking a
 * voucher from being placed into their slots.
 */
public final class VoucherStationListener implements Listener {

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
        if (BLOCKED_STATIONS.contains(event.getInventory().getType())
            && incomingItems(event).stream().anyMatch(this::isVoucher)) {
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

    /**
     * The items a click can move from the player into the view's top inventory. For a click on a
     * top slot: the cursor item, the contents of a bundle on the cursor that places one of them,
     * and the hotbar or offhand item a number key or the swap-offhand key swaps in. For a
     * shift-click in the player's inventory: the clicked item. Resolved from the slot and click
     * type as well as the action, since the server performs a click whatever action it predicted.
     */
    static List<ItemStack> incomingItems(InventoryClickEvent event) {
        List<ItemStack> items = new ArrayList<>();
        Inventory clicked = event.getClickedInventory();
        if (clicked == event.getView().getTopInventory()) {
            ItemStack cursor = event.getCursor();
            items.add(cursor);
            if (event.getAction() == InventoryAction.PLACE_FROM_BUNDLE
                && cursor.getItemMeta() instanceof BundleMeta bundle) {
                // the API does not expose which item the bundle places, so all of them count
                items.addAll(bundle.getItems());
            }
            ItemStack swapped = swappedIn(event);
            if (swapped != null) {
                items.add(swapped);
            }
        } else if (clicked != null
            && (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            ItemStack current = event.getCurrentItem();
            if (current != null) {
                items.add(current);
            }
        }
        return items;
    }

    /**
     * The hotbar or offhand item a number key or the swap-offhand key moves into the clicked
     * slot, or null for any other click.
     */
    @Nullable
    private static ItemStack swappedIn(InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click != ClickType.NUMBER_KEY && click != ClickType.SWAP_OFFHAND
            && event.getAction() != InventoryAction.HOTBAR_SWAP) {
            return null;
        }
        PlayerInventory inventory = event.getWhoClicked().getInventory();
        int button = event.getHotbarButton();
        // An offhand swap reports no usable hotbar button (the value differs across versions).
        return click != ClickType.SWAP_OFFHAND && button >= 0 && button < 9
            ? inventory.getItem(button)
            : inventory.getItemInOffHand();
    }

    private boolean isVoucher(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && stamp.isVoucher(meta);
    }
}
