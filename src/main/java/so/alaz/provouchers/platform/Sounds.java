package so.alaz.provouchers.platform;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Plays a sound from its config form, {@code "key [volume] [pitch]"}, shared by the
 * {@code sound:} reward and a voucher's {@code effects.sound}. The key is read in lower
 * case, since Minecraft keys are lower case by definition.
 */
public final class Sounds {

    private Sounds() {
    }

    /** Parses and plays {@code spec} for {@code player}; a blank spec is a no-op. */
    public static void play(Player player, String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        float volume = parts.length > 1 ? parseFloat(parts[1]) : 1f;
        float pitch = parts.length > 2 ? parseFloat(parts[2]) : 1f;
        player.playSound(Sound.sound(Key.key(key(parts[0])), Sound.Source.MASTER, volume, pitch));
    }

    /**
     * Whether {@code spec} can be played: it is blank (a no-op), or its key parses once lower-cased.
     * Volume and pitch are not checked, since a token that is not a number falls back to {@code 1}.
     */
    public static boolean isValid(String spec) {
        String[] parts = spec.trim().split("\\s+");
        return parts.length == 0 || parts[0].isEmpty() || Key.parseable(key(parts[0]));
    }

    /** The key token in lower case. */
    private static String key(String token) {
        return token.toLowerCase(Locale.ROOT);
    }

    /** A volume or pitch token, defaulting to {@code 1} when it is not a number. */
    private static float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            return 1f;
        }
    }
}
