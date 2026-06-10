package so.alaz.provouchers.voucher;

import java.util.Locale;
import java.util.UUID;

/**
 * A player-head specification for a voucher item: which stable skull source to use
 * and its value. Resolved into an {@code ItemStack} by the skull builder.
 *
 * <p>{@code TEXTURE} and {@code URL} are stable and need no network, so they are
 * the recommended choices for permanent voucher icons. {@code NAME} and
 * {@code UUID} resolve a player's current skin (which can change) and are looked up
 * asynchronously.
 *
 * @param source where the skin comes from
 * @param value  the source value (base64 texture, texture URL, player name, or UUID)
 */
public record SkullSpec(Source source, String value) {

    /** The supported skull sources. */
    public enum Source { TEXTURE, URL, NAME, UUID }

    public SkullSpec {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("skull value must not be blank");
        }
        if (source == Source.UUID) {
            try {
                UUID.fromString(value);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("skull uuid '" + value + "' is not a valid UUID");
            }
        }
    }

    /** Resolves a config {@code source} string to a {@link Source}, with aliases. */
    public static Source source(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "texture", "base64", "value" -> Source.TEXTURE;
            case "url" -> Source.URL;
            case "name", "player" -> Source.NAME;
            case "uuid" -> Source.UUID;
            default -> throw new IllegalArgumentException(
                "unknown skull source '" + raw + "' (use texture, url, name, or uuid)");
        };
    }
}
