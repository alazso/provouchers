package so.alaz.provouchers.util;

import java.util.Map;

/**
 * Converts legacy {@code &}/{@code §} color codes and CrazyVouchers-style tokens into
 * ProVouchers' MiniMessage + percent-placeholder form. MiniMessage tags already present
 * in the input pass through untouched, so mixed-format strings convert cleanly.
 */
public final class LegacyText {

    private static final Map<Character, String> CODES = Map.ofEntries(
        Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
        Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
        Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
        Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
        Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
        Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
        Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
        Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
        Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
        Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
        Map.entry('o', "<italic>"), Map.entry('r', "<reset>"));

    private LegacyText() {
    }

    /** Converts legacy color codes and curly tokens; a string without them is returned as-is. */
    public static String toMiniMessage(String input) {
        String result = convertCodes(input);
        return result
            .replace("{player}", "%player%")
            .replace("{arg}", "%arg%")
            .replaceAll("\\{random:([^}]+)\\}", "%random:$1%");
    }

    private static String convertCodes(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(i + 1));
                // Hex form &#RRGGBB.
                if (next == '#' && i + 7 < input.length()
                    && isHex(input, i + 2, 6)) {
                    out.append('<').append(input, i + 1, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = CODES.get(next);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex(String input, int from, int count) {
        for (int i = from; i < from + count; i++) {
            if (Character.digit(input.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
