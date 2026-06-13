package so.alaz.provouchers.voucher;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import so.alaz.provouchers.util.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A redeem firework: primary colors, optional fade colors, an effect shape, and a flight
 * power. Power {@code 0} (the default) detonates at the player. Colors are the 17 named
 * Bukkit colors, hex values such as {@code "#FF8800"}, or {@code r,g,b} triples.
 */
public record FireworkSpec(List<Color> colors, List<Color> fade, FireworkEffect.Type type, int power) {

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
            out.add(Colors.parse(value));
        }
        return out;
    }
}
