package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

/**
 * The visual definition of a voucher item, kept free of Bukkit types so it can be
 * parsed and tested without a running server. The material is stored as its name
 * and resolved when the item is built.
 *
 * @param material        the Bukkit material name (for example {@code PAPER})
 * @param customItem      an optional custom-item reference (for example
 *                        {@code oraxen:my_item}), resolved through a Strata item hook
 * @param customModelData an optional custom model data value, or {@code null}
 * @param glow            whether the item should have the enchant glint
 */
public record VoucherItem(
    String material,
    @Nullable String customItem,
    @Nullable Integer customModelData,
    boolean glow
) {

    public VoucherItem {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("Voucher item material must not be blank");
        }
    }
}
