package so.alaz.provouchers.antidupe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.strata.api.pdc.PdcKey;

import java.util.UUID;

/**
 * Reads and writes the persistent-data stamp that identifies a voucher item.
 *
 * <p>Every voucher item carries its voucher id. A voucher whose {@code stackable}
 * flag is {@code false} (anti-dupe on) also carries a {@code uid} that is unique
 * per physical item and written at give time; this is what duplicate detection
 * matches on, and it is what stops those items from stacking. A {@code stackable}
 * voucher carries no {@code uid}, so its items stack and are not dupe-tracked.
 */
public final class VoucherStamp {

    private final PdcKey<String, String> idKey;
    private final PdcKey<String, String> uidKey;
    private final PdcKey<Long, Long> givenAtKey;
    private final PdcKey<String, String> ownerKey;
    private final PdcKey<Byte, Byte> warnedKey;

    public VoucherStamp(Plugin plugin) {
        this.idKey = new PdcKey<>(new NamespacedKey(plugin, "voucher_id"), PersistentDataType.STRING);
        this.uidKey = new PdcKey<>(new NamespacedKey(plugin, "uid"), PersistentDataType.STRING);
        this.givenAtKey = new PdcKey<>(new NamespacedKey(plugin, "given_at"), PersistentDataType.LONG);
        this.ownerKey = new PdcKey<>(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING);
        this.warnedKey = new PdcKey<>(new NamespacedKey(plugin, "dupe_warned"), PersistentDataType.BYTE);
    }

    /** A fresh, globally unique id for a single anti-dupe voucher item. */
    public static String newUid() {
        return UUID.randomUUID().toString();
    }

    /** Stamps the voucher id onto freshly created item meta. */
    public void stamp(ItemMeta meta, String voucherId) {
        idKey.set(meta, voucherId);
    }

    /** Whether this meta carries a ProVouchers voucher id. */
    public boolean isVoucher(ItemMeta meta) {
        return idKey.has(meta);
    }

    @Nullable
    public String voucherId(ItemMeta meta) {
        return idKey.get(meta);
    }

    /** Writes the per-item unique id used for duplicate detection. */
    public void setUid(ItemMeta meta, String uid) {
        uidKey.set(meta, uid);
    }

    /** The per-item unique id, or {@code null} for a stackable (non-anti-dupe) voucher. */
    @Nullable
    public String uid(ItemMeta meta) {
        return uidKey.get(meta);
    }

    /** Stamps the epoch-millis time the item was given, used to anchor relative expiry. */
    public void setGivenAt(ItemMeta meta, long epochMillis) {
        givenAtKey.set(meta, epochMillis);
    }

    /** The epoch-millis give time, or {@code null} if the item predates give-time stamping. */
    @Nullable
    public Long givenAt(ItemMeta meta) {
        return givenAtKey.get(meta);
    }

    /** Stamps the owning player's UUID, used to enforce owner-only vouchers. */
    public void setOwner(ItemMeta meta, UUID owner) {
        ownerKey.set(meta, owner.toString());
    }

    /** The owning player's UUID string, or {@code null} if the item has no owner stamp. */
    @Nullable
    public String owner(ItemMeta meta) {
        return ownerKey.get(meta);
    }

    /** Marks the item as already carrying the duplicate warning lore, so it is added once. */
    public void setWarned(ItemMeta meta) {
        warnedKey.set(meta, (byte) 1);
    }

    /** Whether the duplicate warning lore has already been applied to this item. */
    public boolean isWarned(ItemMeta meta) {
        return warnedKey.has(meta);
    }
}
