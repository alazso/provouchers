package so.alaz.provouchers.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Marks an inventory as a ProVouchers menu and carries its {@link GuiSession}. Menus are recognised in
 * events by this holder, never by title.
 */
final class GuiHolder implements InventoryHolder {

    GuiSession session;
    @Nullable Inventory backing;

    @Override
    public Inventory getInventory() {
        return backing;
    }
}
