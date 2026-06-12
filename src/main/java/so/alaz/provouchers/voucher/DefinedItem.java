package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A reusable decorated item defined in a voucher or code file's {@code items:} map and
 * granted by an {@code "item: @<name>"} reward. Carries the same appearance surface as
 * the voucher's own item (material, custom, skull, glow, model data) plus its own
 * display name and lore.
 *
 * @param displayName the MiniMessage display name, or {@code null} to keep the base item's own
 * @param lore        the MiniMessage lore lines
 * @param item        the base item appearance
 */
public record DefinedItem(@Nullable String displayName, List<String> lore, VoucherItem item) {

    public DefinedItem {
        if (item == null) {
            throw new IllegalArgumentException("Defined item needs an item section");
        }
        lore = List.copyOf(lore);
    }
}
