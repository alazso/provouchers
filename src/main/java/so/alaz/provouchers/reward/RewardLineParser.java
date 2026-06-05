package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

/**
 * Parses reward strings of the form {@code "<type>: <payload>"} into
 * {@link RewardLine}s. The keyword is everything before the first colon; the
 * payload is the remainder, with a single leading space trimmed.
 */
public final class RewardLineParser {

    private RewardLineParser() {
    }

    /**
     * Parses a single reward line.
     *
     * @throws IllegalArgumentException if the line has no colon or an unknown keyword
     */
    public static RewardLine parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Reward line must not be null");
        }
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Reward '" + line + "' is missing a '<type>: <value>' "
                + "separator");
        }
        String keyword = line.substring(0, colon);
        RewardType type = RewardType.fromKeyword(keyword);
        if (type == null) {
            throw new IllegalArgumentException("Unknown reward type '" + keyword.trim() + "' in '" + line
                + "'");
        }
        String payload = line.substring(colon + 1);
        if (payload.startsWith(" ")) {
            payload = payload.substring(1);
        }
        return new RewardLine(type, payload);
    }

    /** Parses a line, returning {@code null} instead of throwing on a bad line. */
    @Nullable
    public static RewardLine parseOrNull(String line) {
        try {
            return parse(line);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
