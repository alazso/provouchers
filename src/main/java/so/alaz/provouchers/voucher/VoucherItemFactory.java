package so.alaz.provouchers.voucher;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.strata.api.gui.ItemBuilder;
import so.alaz.strata.api.text.TextRenderer;

import java.util.UUID;

/**
 * Builds the physical {@link ItemStack} for a voucher. When the voucher names a
 * custom provider item (Oraxen, ItemsAdder, and so on via a Strata item hook) that
 * item is used as the base; otherwise a vanilla material is used. Either way the
 * voucher's name, lore, and glow are applied on top, and the persistent-data stamp
 * (id, batch, give time, and owner for owner-only vouchers) is written.
 */
public final class VoucherItemFactory {

    private final TextRenderer text;
    private final VoucherStamp stamp;
    private final ItemResolver items;

    public VoucherItemFactory(TextRenderer text, VoucherStamp stamp, ItemResolver items) {
        this.text = text;
        this.stamp = stamp;
        this.items = items;
    }

    /**
     * Creates {@code amount} copies of {@code voucher}, stamped with {@code batchId}.
     * Names and lore are rendered for {@code viewer} when non-null; for an owner-only
     * voucher, {@code viewer} is recorded as the owner.
     */
    public ItemStack createItem(Voucher voucher, int amount, @Nullable Player viewer, UUID batchId) {
        ItemStack item = buildBase(voucher, amount, viewer);
        item.editMeta(meta -> {
            stamp.stamp(meta, voucher.id(), batchId);
            stamp.setGivenAt(meta, System.currentTimeMillis());
            if (voucher.ownerOnly() && viewer != null) {
                stamp.setOwner(meta, viewer.getUniqueId());
            }
        });
        return item;
    }

    private ItemStack buildBase(Voucher voucher, int amount, @Nullable Player viewer) {
        ItemStack custom = items.custom(voucher.item().customItem());
        if (custom != null) {
            custom.setAmount(amount);
            decorateProvidedItem(custom, voucher, viewer);
            return custom;
        }
        Material material = Materials.resolve(voucher.item().material());
        if (!material.isItem()) {
            throw new IllegalArgumentException(
                "material '" + material.name() + "' is not an obtainable item");
        }
        String name = voucher.displayName() != null ? voucher.displayName() : voucher.id();
        ItemBuilder builder = new ItemBuilder(material)
            .amount(amount)
            .glow(voucher.item().glow())
            .name(text.render(name, viewer));
        if (!voucher.lore().isEmpty()) {
            builder.lore(text.render(voucher.lore(), viewer));
        }
        ItemStack item = builder.build();
        Integer customModelData = voucher.item().customModelData();
        if (customModelData != null) {
            item.editMeta(meta -> meta.setCustomModelData(customModelData));
        }
        return item;
    }

    /**
     * Applies the voucher's name, lore, and glow onto a provider-supplied item.
     * The provider's own model and custom model data are kept; the display name is
     * only overridden when the voucher sets one.
     */
    private void decorateProvidedItem(ItemStack item, Voucher voucher, @Nullable Player viewer) {
        item.editMeta(meta -> {
            if (voucher.displayName() != null) {
                meta.displayName(text.render(voucher.displayName(), viewer)
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (!voucher.lore().isEmpty()) {
                meta.lore(text.render(voucher.lore(), viewer).stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            }
            if (voucher.item().glow() && !meta.hasEnchants()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
    }
}
