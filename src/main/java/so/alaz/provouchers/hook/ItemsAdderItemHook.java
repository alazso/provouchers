package so.alaz.provouchers.hook;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.platform.Classes;

/**
 * ItemsAdder-backed {@link ItemHook}. Ids are ItemsAdder's {@code namespace:id} form. Lookups are
 * guarded and wrapped, so a missing ItemsAdder degrades to {@code null} instead of throwing.
 */
public final class ItemsAdderItemHook implements ItemHook {

    private final boolean present = Classes.present("dev.lone.itemsadder.api.CustomStack", getClass());

    @Override
    public String name() {
        return "ItemsAdder";
    }

    @Override
    public boolean isAvailable() {
        return present && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    @Override
    @Nullable
    public ItemStack createItem(String id) {
        try {
            CustomStack stack = CustomStack.getInstance(id);
            return stack != null ? stack.getItemStack() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
