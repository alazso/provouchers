package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders rewards as short, player-facing text for previews (lore, the preview GUI,
 * and batch-open summaries). Only "loot" rewards (items, currency given, ranks added)
 * are described; cosmetic and command rewards return {@code null} and are skipped, so a
 * preview shows what a player gains rather than how it is delivered. Pure and
 * server-independent: amounts are formatted from the raw payload, so a placeholder amount
 * like {@code %random:1-3%} shows as a range, not a single roll.
 */
public final class RewardDescriber {

    private static final Pattern RANDOM = Pattern.compile("[%{]random:(-?\\d+)-(-?\\d+)(?::\\w+)?[%}]");

    private RewardDescriber() {
    }

    /** A short description of a single reward, or {@code null} when it is not player-facing loot. */
    @Nullable
    public static String describe(RewardLine reward) {
        return switch (reward.type()) {
            case ITEM -> {
                RewardItemPayload item = RewardItemPayload.parse(reward.payload());
                yield amount(item.amount()) + "x " + humanize(item.reference());
            }
            case CURRENCY -> {
                CurrencyRewardPayload currency = CurrencyRewardPayload.parse(reward.payload());
                // Taking money is a cost, not loot, so it is not previewed.
                yield currency.action() == CurrencyRewardPayload.Action.GIVE
                    ? amount(currency.amount()) + " currency" : null;
            }
            case GROUP -> {
                GroupRewardPayload group = GroupRewardPayload.parse(reward.payload());
                yield group.action() == GroupRewardPayload.Action.ADD
                    ? group.group() + " rank" + (group.isTemporary() ? " (temporary)" : "") : null;
            }
            default -> null; // commands, messages, titles, action bars, sounds, permissions
        };
    }

    /** Describes every loot reward in order; non-loot and malformed lines are dropped. */
    public static List<String> describeAll(List<RewardLine> rewards) {
        List<String> out = new ArrayList<>();
        for (RewardLine reward : rewards) {
            String line = safeDescribe(reward);
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * Describes weighted random sets, each prefixed with its chance percentage. A set's
     * loot lines are joined with {@code " + "}; a set with no previewable loot shows a
     * generic {@code "a reward"}.
     */
    public static List<String> describeRandom(List<RewardSet> sets) {
        double total = sets.stream().mapToDouble(RewardSet::weight).sum();
        List<String> out = new ArrayList<>();
        for (RewardSet set : sets) {
            List<String> parts = describeAll(set.rewards());
            String body = parts.isEmpty() ? "a reward" : String.join(" + ", parts);
            int percent = total > 0 ? (int) Math.round(set.weight() / total * 100.0) : 0;
            out.add(percent + "%: " + body);
        }
        return out;
    }

    @Nullable
    private static String safeDescribe(RewardLine reward) {
        try {
            return describe(reward);
        } catch (IllegalArgumentException ex) {
            return null; // a malformed payload is simply not previewed
        }
    }

    /** A placeholder amount like {@code %random:1-3%} becomes {@code "1-3"}; a literal passes through. */
    private static String amount(String raw) {
        Matcher matcher = RANDOM.matcher(raw.trim());
        return matcher.matches() ? matcher.group(1) + "-" + matcher.group(2) : raw.trim();
    }

    /** Title-cases a vanilla material ({@code DIAMOND_SWORD} to {@code Diamond Sword}); other refs pass through. */
    private static String humanize(String reference) {
        if (reference.startsWith("serialized:")) {
            return "a custom item";
        }
        if (reference.indexOf(':') >= 0) {
            return reference; // provider:id custom item, shown as written
        }
        StringBuilder out = new StringBuilder();
        for (String word : reference.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? reference : out.toString();
    }
}
