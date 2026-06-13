package so.alaz.provouchers.util;

import org.bukkit.Color;

import java.util.Locale;
import java.util.Map;

/**
 * Parses a color from config: one of the 17 named colors, a {@code #RRGGBB} hex value, or an
 * {@code r,g,b} triple. Shared by item dye colors and firework colors.
 */
public final class Colors {

    private static final Map<String, Color> NAMED = Map.ofEntries(
        Map.entry("AQUA", Color.AQUA), Map.entry("BLACK", Color.BLACK),
        Map.entry("BLUE", Color.BLUE), Map.entry("FUCHSIA", Color.FUCHSIA),
        Map.entry("GRAY", Color.GRAY), Map.entry("GREEN", Color.GREEN),
        Map.entry("LIME", Color.LIME), Map.entry("MAROON", Color.MAROON),
        Map.entry("NAVY", Color.NAVY), Map.entry("OLIVE", Color.OLIVE),
        Map.entry("ORANGE", Color.ORANGE), Map.entry("PURPLE", Color.PURPLE),
        Map.entry("RED", Color.RED), Map.entry("SILVER", Color.SILVER),
        Map.entry("TEAL", Color.TEAL), Map.entry("WHITE", Color.WHITE),
        Map.entry("YELLOW", Color.YELLOW));

    private Colors() {
    }

    /** Parses a named color, {@code #RRGGBB} hex, or {@code r,g,b}; throws on anything else. */
    public static Color parse(String raw) {
        String value = raw.trim();
        Color named = NAMED.get(value.toUpperCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            } catch (NumberFormatException ignored) {
                // falls through to the shared error
            }
        }
        if (value.contains(",")) {
            String[] parts = value.split("\\s*,\\s*");
            if (parts.length == 3) {
                try {
                    return Color.fromRGB(channel(parts[0]), channel(parts[1]), channel(parts[2]));
                } catch (NumberFormatException ignored) {
                    // falls through to the shared error
                }
            }
        }
        throw new IllegalArgumentException(
            "color '" + raw + "' is not a named color, #RRGGBB hex, or r,g,b");
    }

    private static int channel(String value) {
        int channel = Integer.parseInt(value.trim());
        if (channel < 0 || channel > 255) {
            throw new NumberFormatException("channel out of range: " + channel);
        }
        return channel;
    }
}
