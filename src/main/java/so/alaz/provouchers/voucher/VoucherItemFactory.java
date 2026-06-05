package so.alaz.provouchers.voucher;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.strata.api.gui.ItemBuilder;
import so.alaz.strata.api.text.TextRenderer;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds the physical {@link ItemStack} for a voucher: resolves the material,
 * renders the MiniMessage name and lore through Strata's text renderer (so
 * placeholders render per viewer), applies custom model data, and writes the
 * persistent-data stamp that ties the item to its batch.
 */
public final class VoucherItemFactory {

    private final TextRenderer text;
    private final VoucherStamp stamp;

    public VoucherItemFactory(TextRenderer text, VoucherStamp stamp) {
        this.text = text;
        this.stamp = stamp;
    }

    /**
     * Creates {@code amount} copies of {@code voucher}, stamped with {@code batchId}.
     * Names and lore are rendered for {@code viewer} when non-null.
     */
    public ItemStack createItem(Voucher voucher, int amount, @Nullable Player viewer, UUID batchId) {
        Material material = resolveMaterial(voucher.item().material());
        ItemBuilder builder = new ItemBuilder(material)
            .amount(amount)
            .glow(voucher.item().glow())
            .name(text.render(voucher.displayName(), viewer));
        if (!voucher.lore().isEmpty()) {
            builder.lore(text.render(voucher.lore(), viewer));
        }
        ItemStack item = builder.build();
        item.editMeta(meta -> {
            stamp.stamp(meta, voucher.id(), batchId);
            Integer customModelData = voucher.item().customModelData();
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
        });
        return item;
    }

    private static Material resolveMaterial(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            material = Material.getMaterial(name.toUpperCase(Locale.ROOT));
        }
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Unknown or non-item material '" + name + "'");
        }
        return material;
    }
}
