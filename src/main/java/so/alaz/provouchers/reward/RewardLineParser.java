package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.voucher.CustomItemRef;
import so.alaz.provouchers.voucher.Materials;

/**
 * Parses reward strings of the form {@code "<type>: <payload>"} into
 * {@link RewardLine}s. The keyword is everything before the first colon; the
 * payload is the remainder, with a single leading space trimmed. A provider
 * keyword (such as {@code itemsadder}) is folded into the item reference, and
 * item rewards are validated so material typos and bad amounts fail at load.
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
        String providerPrefix = RewardType.providerPrefix(keyword);
        if (providerPrefix != null) {
            payload = providerPrefix + ":" + payload;
        }
        switch (type) {
            case ITEM -> validateItemPayload(payload);
            case CURRENCY -> CurrencyRewardPayload.parse(payload);
            case GROUP -> GroupRewardPayload.parse(payload);
            case PERMISSION -> PermissionRewardPayload.parse(payload);
            default -> { /* other reward types have free-form payloads */ }
        }
        return new RewardLine(type, payload);
    }

    private static void validateItemPayload(String payload) {
        RewardItemPayload parsed = RewardItemPayload.parse(payload);
        String reference = parsed.reference();
        if (reference.indexOf(':') < 0) {
            Materials.resolve(reference);   // vanilla material must exist
        } else {
            // Provider item: validate the prefix and syntax now; item existence is checked at runtime.
            CustomItemRef ref = CustomItemRef.parse(reference);
            if (!ref.hasKnownProvider()) {
                throw new IllegalArgumentException("unknown item provider '" + ref.providerHint() + "'");
            }
        }
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
