package so.alaz.provouchers.hook;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.platform.Classes;

/**
 * Oraxen-backed {@link ItemHook}. Lookups are guarded and wrapped, so a missing Oraxen degrades to
 * {@code null} instead of throwing.
 */
public final class OraxenItemHook implements ItemHook {

    private final boolean present = Classes.present("io.th0rgal.oraxen.api.OraxenItems", getClass());

    @Override
    public String name() {
        return "Oraxen";
    }

    @Override
    public boolean isAvailable() {
        return present && Bukkit.getPluginManager().isPluginEnabled("Oraxen");
    }

    @Override
    @Nullable
    public ItemStack createItem(String id) {
        try {
            return OraxenItems.exists(id) ? OraxenItems.getItemById(id).build() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
