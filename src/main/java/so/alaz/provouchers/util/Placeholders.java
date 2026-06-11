package so.alaz.provouchers.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes the dynamic placeholders ProVouchers supports inside reward and message
 * strings: {@code %player%} for the redeemer's name, {@code %arg%} for a parametric
 * voucher's argument, and {@code %random:min-max%} for a random integer in the
 * inclusive range. The curly-brace forms ({@code {player}}, {@code {arg}},
 * {@code {random:min-max}}) are still honoured for backward compatibility but are
 * deprecated; the config loader warns when it sees them.
 *
 * <p>These are ProVouchers' own placeholders, distinct from PlaceholderAPI. MiniMessage
 * markup and PlaceholderAPI placeholders are intentionally left untouched here; those
 * are resolved later by the text renderer.
 */
public final class Placeholders {

    /** Matches the random placeholder in either the {@code %random:a-b%} or legacy {@code {random:a-b}} form. */
    private static final Pattern RANDOM = Pattern.compile("[%{]random:(-?\\d+)-(-?\\d+)[%}]");

    private Placeholders() {
    }

    /** Applies all placeholders using the shared thread-local random for {@code %random:%}. */
    public static String apply(String input, String playerName, @Nullable String arg) {
        return apply(input, playerName, arg, ThreadLocalRandom.current());
    }

    /**
     * Applies all placeholders to each line, returning a new list. {@code %random:%} is drawn
     * independently per line, so a fixed roll is baked into the line at the moment of
     * substitution (e.g. when a voucher item's lore is built).
     */
    public static List<String> applyAll(List<String> lines, String playerName, @Nullable String arg) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(apply(line, playerName, arg));
        }
        return out;
    }

    /** Applies all placeholders, drawing {@code %random:min-max%} values from {@code random}. */
    public static String apply(String input, String playerName, @Nullable String arg, Random random) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String argValue = arg == null ? "" : arg;
        String result = input
            .replace("%player%", playerName)
            .replace("{player}", playerName)   // deprecated curly-brace form
            .replace("%arg%", argValue)
            .replace("{arg}", argValue);        // deprecated curly-brace form
        return applyRandom(result, random);
    }

    private static String applyRandom(String input, Random random) {
        if (input.indexOf("random:") < 0) {
            return input;
        }
        Matcher matcher = RANDOM.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            long low = Long.parseLong(matcher.group(1));
            long high = Long.parseLong(matcher.group(2));
            if (low > high) {
                long swap = low;
                low = high;
                high = swap;
            }
            long span = high - low + 1L;
            long value = low + Math.floorMod(random.nextLong(), span);
            matcher.appendReplacement(out, Long.toString(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
