package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.reward.WebhookSpec;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable, parsed voucher definition. Conditions are kept as their raw config
 * maps and built into {@code Condition}s at redemption time, so this type
 * stays independent of the running server.
 *
 * @param id            the unique voucher id (its file name without extension)
 * @param displayName   the MiniMessage display name, or {@code null} to keep the
 *                      base item's own name (relevant for custom provider items)
 * @param lore          the MiniMessage lore lines
 * @param item          the item appearance
 * @param conditionMaps raw condition sections, each a {@code type} plus its keys
 * @param rewards       reward lines that always run on a successful redeem
 * @param randomRewards weighted reward sets, exactly one of which runs per redeem
 * @param definedItems  decorated items from the {@code items:} map, granted via {@code item: @name}
 * @param unredeemable  if {@code true} the item exists for show and cannot be redeemed
 * @param ownerOnly     if {@code true} only the player it was given to may redeem it
 * @param cooldownSeconds per-player cooldown between redemptions, in seconds
 * @param maxUses       global redemption cap across all players, or {@code -1} for unlimited
 * @param usesPerPlayer lifetime redemptions allowed per player, or {@code -1} for unlimited
 * @param expiry        raw expiry value (ISO-8601 or relative), or {@code null}
 * @param activeFrom    raw absolute instant before which redeeming is refused, or {@code null}
 * @param hasArgument   if {@code true} a free-form argument is accepted and exposed as {@code %arg%}
 * @param stackable     if {@code true} (the default) items stack and are not dupe-tracked; if
 *                      {@code false}, each item is stamped with a unique id for anti-dupe and will
 *                      not stack
 * @param batchOpen     if {@code true}, shift-right-clicking a stack redeems every item at once;
 *                      requires a stackable voucher with no cooldown
 * @param twoStep       if {@code true}, redeeming requires a confirmation click within the window
 * @param confirmMessage the per-voucher confirmation prompt, or {@code null} to use the locale default
 * @param effects       optional sound and particle played to the redeemer, or {@code null} for none
 * @param soulbound     transfer restrictions binding the item to its player, or {@code null}
 * @param enabled       if {@code false} the voucher loads but cannot be redeemed (an admin toggle)
 * @param discordWebhooks named Discord webhook targets for {@code discord: @name} rewards, keyed by lower-cased name
 */
public record Voucher(
    String id,
    @Nullable String displayName,
    List<String> lore,
    VoucherItem item,
    List<Map<String, Object>> conditionMaps,
    List<RewardLine> rewards,
    List<RewardSet> randomRewards,
    Map<String, DefinedItem> definedItems,
    boolean unredeemable,
    boolean ownerOnly,
    long cooldownSeconds,
    int maxUses,
    int usesPerPlayer,
    @Nullable String expiry,
    @Nullable String activeFrom,
    boolean hasArgument,
    boolean stackable,
    boolean batchOpen,
    boolean twoStep,
    @Nullable String confirmMessage,
    @Nullable VoucherEffects effects,
    @Nullable SoulboundSpec soulbound,
    boolean enabled,
    Map<String, WebhookSpec> discordWebhooks
) implements so.alaz.provouchers.api.Voucher {

    public Voucher {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Voucher id must not be blank");
        }
        lore = List.copyOf(lore);
        conditionMaps = List.copyOf(conditionMaps);
        rewards = List.copyOf(rewards);
        randomRewards = List.copyOf(randomRewards);
        definedItems = Map.copyOf(definedItems);
        discordWebhooks = Map.copyOf(discordWebhooks);
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("Voucher '" + id + "' cooldown must not be negative");
        }
        if (maxUses == 0 || maxUses < -1) {
            throw new IllegalArgumentException("Voucher '" + id + "' max-uses must be -1 or at least 1");
        }
        if (usesPerPlayer == 0 || usesPerPlayer < -1) {
            throw new IllegalArgumentException("Voucher '" + id + "' uses-per-player must be -1 or at least 1");
        }
    }

    /** Whether this voucher grants any reward at all. */
    public boolean hasRewards() {
        return !rewards.isEmpty() || !randomRewards.isEmpty();
    }

    /** Whether a global redemption cap is configured. */
    public boolean hasGlobalLimit() {
        return maxUses > 0;
    }

    /** Whether a per-player lifetime limit is configured. */
    public boolean hasPerPlayerLimit() {
        return usesPerPlayer > 0;
    }

    /** Whether any use limit is configured, requiring the persistent use check. */
    public boolean hasUseLimits() {
        return hasGlobalLimit() || hasPerPlayerLimit();
    }

    /** This voucher's persistent use-counter key. See {@link #voucherUseKey(String)}. */
    public String useKey() {
        return voucherUseKey(id);
    }

    /**
     * The use-counter storage key for a voucher id, namespaced apart from code keys (which
     * are stored raw). The counter column holds 64 characters, so an id used with limits
     * must fit {@code "voucher:" + id}; the parser enforces this at load.
     */
    public static String voucherUseKey(String id) {
        return "voucher:" + id.toLowerCase(Locale.ROOT);
    }
}
