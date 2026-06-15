package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.reward.WebhookSpec;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable, typeable redemption code. Codes share the reward and condition
 * model of {@link Voucher}s but are redeemed by name through
 * {@code /voucher redeem <code>} rather than by holding an item.
 *
 * @param code          the literal code players type
 * @param caseSensitive whether {@link #matches(String)} is case-sensitive
 * @param maxUses       global redemption cap across all players, or {@code -1} for unlimited
 * @param usesPerPlayer how many times a single player may redeem it
 * @param expiry        raw expiry value (ISO-8601 or relative), or {@code null}
 * @param activeFrom    raw absolute instant before which redeeming is refused, or {@code null}
 * @param conditionMaps raw condition sections
 * @param rewards       always-run reward lines
 * @param randomRewards weighted reward sets
 * @param definedItems  decorated items from the {@code items:} map, granted via {@code item: @name}
 * @param hasArgument   whether a free-form argument is accepted
 * @param enabled       if {@code false} the code loads but cannot be redeemed (an admin toggle)
 * @param effects       optional sound and firework played on a successful redeem, or {@code null}
 * @param discordWebhooks named Discord webhook targets for {@code discord: @name} rewards, keyed by lower-cased name
 */
public record VoucherCode(
    String code,
    boolean caseSensitive,
    int maxUses,
    int usesPerPlayer,
    @Nullable String expiry,
    @Nullable String activeFrom,
    List<Map<String, Object>> conditionMaps,
    List<RewardLine> rewards,
    List<RewardSet> randomRewards,
    Map<String, DefinedItem> definedItems,
    boolean hasArgument,
    boolean enabled,
    @Nullable VoucherEffects effects,
    Map<String, WebhookSpec> discordWebhooks
) implements so.alaz.provouchers.api.VoucherCode {

    public VoucherCode {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code must not be blank");
        }
        if (usesPerPlayer < 1) {
            throw new IllegalArgumentException("Code '" + code + "' uses-per-player must be at least 1");
        }
        conditionMaps = List.copyOf(conditionMaps);
        rewards = List.copyOf(rewards);
        randomRewards = List.copyOf(randomRewards);
        definedItems = Map.copyOf(definedItems);
        discordWebhooks = Map.copyOf(discordWebhooks);
    }

    /** The lookup key for this code: itself, or its lower-cased form when case-insensitive. */
    public String key() {
        return caseSensitive ? code : code.toLowerCase(Locale.ROOT);
    }

    /** Whether {@code input} redeems this code, honouring {@link #caseSensitive()}. */
    public boolean matches(String input) {
        return caseSensitive ? code.equals(input) : code.equalsIgnoreCase(input);
    }

    /** Whether this code has a global use cap. */
    public boolean hasGlobalLimit() {
        return maxUses >= 0;
    }
}
