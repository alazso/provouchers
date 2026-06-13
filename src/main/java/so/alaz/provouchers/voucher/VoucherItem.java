package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The visual definition of a voucher item, kept free of Bukkit types so it can be
 * parsed and tested without a running server. The material and enchantments are stored
 * as their names and resolved when the item is built.
 *
 * @param material        the Bukkit material name (for example {@code PAPER})
 * @param customItem      an optional custom-item reference (for example
 *                        {@code oraxen:my_item}), resolved through an item hook
 * @param customModelData an optional custom model data value, or {@code null}
 * @param glow            whether the item should have the enchant glint
 * @param skull           an optional player-head specification; when set the item is
 *                        a custom head, built through the skull builder
 * @param enchantments    enchantment key to level, resolved against the registry at build time
 */
public record VoucherItem(
    String material,
    @Nullable String customItem,
    @Nullable Integer customModelData,
    boolean glow,
    @Nullable SkullSpec skull,
    Map<String, Integer> enchantments
) {

    public VoucherItem {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("Voucher item material must not be blank");
        }
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }
}
