package so.alaz.provouchers.hook;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.platform.Classes;

/**
 * Nexo-backed {@link ItemHook} (Nexo is the successor to Oraxen). Lookups are guarded and wrapped,
 * so a missing Nexo degrades to {@code null} instead of throwing.
 */
public final class NexoItemHook implements ItemHook {

    private final boolean present = Classes.present("com.nexomc.nexo.api.NexoItems", getClass());

    @Override
    public String name() {
        return "Nexo";
    }

    @Override
    public boolean isAvailable() {
        return present && Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    @Override
    @Nullable
    public ItemStack createItem(String id) {
        try {
            if (!NexoItems.exists(id)) {
                return null;
            }
            ItemBuilder builder = NexoItems.itemFromId(id);
            return builder != null ? builder.build() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
