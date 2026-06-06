package so.alaz.provouchers.antidupe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.strata.api.pdc.PdcKey;

import java.util.UUID;

/**
 * Reads and writes the persistent-data stamp that identifies a voucher item and
 * powers duplicate detection.
 *
 * <p>Two values are stored. The {@code batchId} is shared by every item handed out
 * in one give operation and is written when the item is created. The {@code nonce}
 * is unique per physical item and is assigned lazily, on first interaction, so
 * that bulk-given stacks stay stackable until a player actually uses one.
 */
public final class VoucherStamp {

    private final PdcKey<String, String> idKey;
    private final PdcKey<String, String> batchKey;
    private final PdcKey<String, String> nonceKey;
    private final PdcKey<Long, Long> givenAtKey;
    private final PdcKey<String, String> ownerKey;

    public VoucherStamp(Plugin plugin) {
        this.idKey = new PdcKey<>(new NamespacedKey(plugin, "voucher_id"), PersistentDataType.STRING);
        this.batchKey = new PdcKey<>(new NamespacedKey(plugin, "batch_id"), PersistentDataType.STRING);
        this.nonceKey = new PdcKey<>(new NamespacedKey(plugin, "nonce"), PersistentDataType.STRING);
        this.givenAtKey = new PdcKey<>(new NamespacedKey(plugin, "given_at"), PersistentDataType.LONG);
        this.ownerKey = new PdcKey<>(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING);
    }

    /** A fresh, globally unique nonce for a single voucher item. */
    public static String newNonce() {
        return UUID.randomUUID().toString();
    }

    /** Stamps the voucher id and batch id onto freshly created item meta. */
    public void stamp(ItemMeta meta, String voucherId, UUID batchId) {
        idKey.set(meta, voucherId);
        batchKey.set(meta, batchId.toString());
    }

    /** Whether this meta carries a ProVouchers voucher id. */
    public boolean isVoucher(ItemMeta meta) {
        return idKey.has(meta);
    }

    @Nullable
    public String voucherId(ItemMeta meta) {
        return idKey.get(meta);
    }

    @Nullable
    public String batchId(ItemMeta meta) {
        return batchKey.get(meta);
    }

    @Nullable
    public String nonce(ItemMeta meta) {
        return nonceKey.get(meta);
    }

    public boolean hasNonce(ItemMeta meta) {
        return nonceKey.has(meta);
    }

    /** Writes a nonce onto the meta (caller must persist it with {@code setItemMeta}). */
    public void setNonce(ItemMeta meta, String nonce) {
        nonceKey.set(meta, nonce);
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
}
