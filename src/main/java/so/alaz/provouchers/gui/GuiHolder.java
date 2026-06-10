package so.alaz.provouchers.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Marks an inventory as a ProVouchers menu and carries its {@link GuiSession}. Holder-based
 * identity is the Paper-recommended way to recognise your inventories in events; never match on
 * title.
 */
final class GuiHolder implements InventoryHolder {

    GuiSession session;
    @Nullable Inventory backing;

    @Override
    public Inventory getInventory() {
        return backing;
    }
}
