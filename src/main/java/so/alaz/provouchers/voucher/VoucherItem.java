package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The visual definition of a voucher item, kept free of Bukkit types so it can be
 * parsed and tested without a running server. The material, enchantments, trim, and
 * color are stored as strings and resolved when the item is built.
 *
 * @param material        the Bukkit material name (for example {@code PAPER})
 * @param customItem      an optional custom-item reference (for example
 *                        {@code oraxen:my_item}), resolved through an item hook
 * @param glow            whether the item shows the enchant glint
 * @param skull           an optional player-head specification; when set the item is
 *                        a custom head, built through the skull builder
 * @param enchantments    enchantment key to level, resolved against the registry at build time
 * @param damage          durability damage to apply, or {@code null} for a pristine item
 * @param itemModel       an optional {@code namespace:key} item model
 * @param color           an optional dye color (leather armor, potions, maps), resolved at build time
 */
public record VoucherItem(
    String material,
    @Nullable String customItem,
    @Nullable Integer customModelData,
    boolean glow,
    @Nullable SkullSpec skull,
    Map<String, Integer> enchantments,
    @Nullable Integer damage,
    boolean unbreakable,
    @Nullable ItemTrim trim,
    @Nullable String itemModel,
    boolean hideTooltip,
    @Nullable String color
) {

    public VoucherItem {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("Voucher item material must not be blank");
        }
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }
}
