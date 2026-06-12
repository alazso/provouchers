package so.alaz.provouchers.cooldown;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Permission-based cooldown multipliers from {@code cooldown.tiers} in config.yml. A player
 * holding {@code provouchers.cooldown.<tier>} gets that tier's multiplier on every voucher
 * cooldown; when they hold several, the lowest wins. {@code 0} removes the cooldown.
 */
public final class CooldownTiers {

    public static final String PERMISSION_PREFIX = "provouchers.cooldown.";

    private final Map<String, Double> tiers;

    public CooldownTiers(Map<String, Double> tiers) {
        this.tiers = Map.copyOf(tiers);
    }

    public static CooldownTiers none() {
        return new CooldownTiers(Map.of());
    }

    /** The lowest multiplier among tiers whose permission the player holds, or {@code 1.0}. */
    public double multiplier(Predicate<String> hasPermission) {
        double result = 1.0;
        for (Map.Entry<String, Double> tier : tiers.entrySet()) {
            double value = Math.max(0.0, tier.getValue());
            if (value < result && hasPermission.test(PERMISSION_PREFIX + tier.getKey())) {
                result = value;
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return tiers.isEmpty();
    }
}
