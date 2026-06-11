package so.alaz.provouchers.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes the dynamic placeholders ProVouchers supports inside reward and message
 * strings: {@code %player%} for the redeemer's name, {@code %arg%} for a parametric
 * voucher's argument, and {@code %random:min-max%} for a random integer in the
 * inclusive range. A random can be named ({@code %random:min-max:name%}): the first
 * occurrence rolls and caches under that name, and later occurrences with the same name
 * reuse the value, so one roll can be given out and announced together. The curly-brace
 * forms ({@code {player}}, {@code {arg}}, {@code {random:min-max}}) are still honoured
 * for backward compatibility but are deprecated; the config loader warns when it sees them.
 *
 * <p>These are ProVouchers' own placeholders, distinct from PlaceholderAPI. MiniMessage
 * markup and PlaceholderAPI placeholders are intentionally left untouched here; those
 * are resolved later by the text renderer.
 */
public final class Placeholders {

    /** Random placeholder, with an optional capture name: {@code %random:a-b%} or {@code %random:a-b:name%}. */
    private static final Pattern RANDOM = Pattern.compile("[%{]random:(-?\\d+)-(-?\\d+)(?::(\\w+))?[%}]");

    private Placeholders() {
    }

    /** Applies all placeholders using the shared thread-local random for {@code %random:%}. */
    public static String apply(String input, String playerName, @Nullable String arg) {
        return apply(input, playerName, arg, ThreadLocalRandom.current());
    }

    /** Applies all placeholders, drawing {@code %random:min-max%} values from {@code random}. */
    public static String apply(String input, String playerName, @Nullable String arg, Random random) {
        return apply(input, playerName, arg, random, new HashMap<>());
    }

    /**
     * Applies all placeholders, sharing {@code namedRolls} so a named random
     * ({@code %random:a-b:name%}) reuses one roll across calls (e.g. across the reward lines of a
     * single redeem). Plain {@code %random:a-b%} is independent every time.
     */
    public static String apply(String input, String playerName, @Nullable String arg, Random random,
                               Map<String, Long> namedRolls) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String argValue = arg == null ? "" : arg;
        String result = input
            .replace("%player%", playerName)
            .replace("{player}", playerName)   // deprecated curly-brace form
            .replace("%arg%", argValue)
            .replace("{arg}", argValue);        // deprecated curly-brace form
        return applyRandom(result, random, namedRolls);
    }

    /**
     * Applies all placeholders to each line, returning a new list. Named randoms are shared across
     * the lines (so an item's lore is internally consistent); plain randoms are drawn per line.
     */
    public static List<String> applyAll(List<String> lines, String playerName, @Nullable String arg) {
        Map<String, Long> namedRolls = new HashMap<>();
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(apply(line, playerName, arg, ThreadLocalRandom.current(), namedRolls));
        }
        return out;
    }

    private static String applyRandom(String input, Random random, Map<String, Long> namedRolls) {
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
            String name = matcher.group(3);
            long value;
            if (name != null && namedRolls.containsKey(name)) {
                value = namedRolls.get(name);
            } else {
                value = low + Math.floorMod(random.nextLong(), high - low + 1L);
                if (name != null) {
                    namedRolls.put(name, value);
                }
            }
            matcher.appendReplacement(out, Long.toString(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
