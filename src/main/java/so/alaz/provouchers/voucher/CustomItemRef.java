package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * A parsed reference to a third-party custom item, such as
 * {@code itemsadder:ax_wings_pack:phoenix_wings} or {@code oraxen:my_item}.
 *
 * <p>The text before the first colon is treated as a provider hint (matched
 * case-insensitively against an item hook's name); the remainder is the
 * provider's own item id, which may itself contain colons.
 *
 * @param providerHint the provider name hint, or {@code null} if none was given
 * @param id           the provider's item id
 */
public record CustomItemRef(@Nullable String providerHint, String id) {

    /** The provider prefixes ProVouchers recognizes, including aliases ({@code ia}, {@code hdb}). */
    private static final Set<String> KNOWN_PROVIDERS =
        Set.of("itemsadder", "ia", "oraxen", "nexo", "headdatabase", "hdb");

    public CustomItemRef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Custom item id must not be blank");
        }
    }

    /** Whether the reference names a recognized provider (or none, for an unqualified id). */
    public boolean hasKnownProvider() {
        return providerHint == null || KNOWN_PROVIDERS.contains(providerHint.toLowerCase(Locale.ROOT));
    }

    /** Splits {@code reference} on its first colon into a provider hint and an id. */
    public static CustomItemRef parse(String reference) {
        int colon = reference.indexOf(':');
        if (colon < 0) {
            return new CustomItemRef(null, reference);
        }
        String hint = reference.substring(0, colon);
        String id = reference.substring(colon + 1);
        return new CustomItemRef(hint.isBlank() ? null : hint, id);
    }
}
