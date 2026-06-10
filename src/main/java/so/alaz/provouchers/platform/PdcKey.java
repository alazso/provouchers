package so.alaz.provouchers.platform;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Typed read/write wrapper over a single persistent-data key, removing the raw-key
 * boilerplate and unchecked casting that PDC access otherwise requires.
 *
 * <p>{@code P} is the primitive storage type and {@code C} the complex (API) type, matching
 * {@link PersistentDataType}. Writes mutate the holder's container in place; for
 * {@link org.bukkit.inventory.meta.ItemMeta} you must still call {@code ItemStack#setItemMeta}
 * afterwards.
 */
public final class PdcKey<P, C> {

    private final NamespacedKey key;
    private final PersistentDataType<P, C> type;

    public PdcKey(NamespacedKey key, PersistentDataType<P, C> type) {
        this.key = key;
        this.type = type;
    }

    /** Reads the value from the holder's container, or {@code null} if absent. */
    @Nullable
    public C get(PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().get(key, type);
    }

    /** Whether the holder's container holds this key with the matching type. */
    public boolean has(PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().has(key, type);
    }

    /** Writes the value into the holder's container. */
    public void set(PersistentDataHolder holder, C value) {
        holder.getPersistentDataContainer().set(key, type, value);
    }
}
