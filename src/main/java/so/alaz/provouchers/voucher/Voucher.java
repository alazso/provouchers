package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSet;

import java.util.List;
import java.util.Map;

/**
 * An immutable, parsed voucher definition. Conditions are kept as their raw config
 * maps and built into Strata {@code Condition}s at redemption time, so this type
 * stays independent of the running server.
 *
 * @param id            the unique voucher id (its file name without extension)
 * @param displayName   the MiniMessage display name
 * @param lore          the MiniMessage lore lines
 * @param item          the item appearance
 * @param conditionMaps raw condition sections, each a {@code type} plus its keys
 * @param rewards       reward lines that always run on a successful redeem
 * @param randomRewards weighted reward sets, exactly one of which runs per redeem
 * @param unredeemable  if {@code true} the item exists for show and cannot be redeemed
 * @param ownerOnly     if {@code true} only the player it was given to may redeem it
 * @param cooldownSeconds per-player cooldown between redemptions, in seconds
 * @param expiry        raw expiry value (ISO-8601 or relative), or {@code null}
 * @param hasArgument   if {@code true} a free-form argument is accepted and exposed as {@code {arg}}
 */
public record Voucher(
    String id,
    String displayName,
    List<String> lore,
    VoucherItem item,
    List<Map<String, Object>> conditionMaps,
    List<RewardLine> rewards,
    List<RewardSet> randomRewards,
    boolean unredeemable,
    boolean ownerOnly,
    long cooldownSeconds,
    @Nullable String expiry,
    boolean hasArgument
) {

    public Voucher {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Voucher id must not be blank");
        }
        lore = List.copyOf(lore);
        conditionMaps = List.copyOf(conditionMaps);
        rewards = List.copyOf(rewards);
        randomRewards = List.copyOf(randomRewards);
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("Voucher '" + id + "' cooldown must not be negative");
        }
    }

    /** Whether this voucher grants any reward at all. */
    public boolean hasRewards() {
        return !rewards.isEmpty() || !randomRewards.isEmpty();
    }
}
