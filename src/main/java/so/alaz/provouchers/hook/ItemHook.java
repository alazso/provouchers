package so.alaz.provouchers.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Custom-item lookups behind one interface (ItemsAdder, Oraxen, Nexo, HeadDatabase). */
public interface ItemHook extends Hook {

    /** Builds the custom item for the id, or {@code null} if the id is unknown to this provider. */
    @Nullable
    ItemStack createItem(String id);
}
