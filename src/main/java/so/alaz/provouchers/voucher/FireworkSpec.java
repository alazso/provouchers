package so.alaz.provouchers.voucher;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A redeem firework: primary colors, optional fade colors, an effect shape, and a flight
 * power. Power {@code 0} (the default) detonates at the player. Colors are the 17 named
 * Bukkit colors or hex values such as {@code "#FF8800"}.
 */
public record FireworkSpec(List<Color> colors, List<Color> fade, FireworkEffect.Type type, int power) {

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

    public FireworkSpec {
        if (colors == null || colors.isEmpty()) {
            throw new IllegalArgumentException("firework needs at least one color");
        }
        colors = List.copyOf(colors);
        fade = fade == null ? List.of() : List.copyOf(fade);
        if (power < 0 || power > 2) {
            throw new IllegalArgumentException("firework power must be 0 to 2, was " + power);
        }
    }

    /** Builds a spec from raw config values, resolving color names, hex values, and the shape. */
    public static FireworkSpec of(List<String> colors, List<String> fade, String type, int power) {
        FireworkEffect.Type shape;
        try {
            shape = FireworkEffect.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("firework type '" + type + "' is not a valid shape");
        }
        return new FireworkSpec(parseColors(colors), parseColors(fade), shape, power);
    }

    private static List<Color> parseColors(List<String> raw) {
        List<Color> out = new ArrayList<>(raw.size());
        for (String value : raw) {
            out.add(parseColor(value));
        }
        return out;
    }

    /** A named Bukkit color or a {@code #RRGGBB} hex value. */
    public static Color parseColor(String raw) {
        String value = raw.trim();
        Color named = NAMED.get(value.toUpperCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            } catch (NumberFormatException ignored) {
                // falls through to the shared error below
            }
        }
        throw new IllegalArgumentException("firework color '" + raw + "' is not a named color or #RRGGBB hex");
    }
}
