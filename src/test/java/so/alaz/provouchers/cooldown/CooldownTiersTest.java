package so.alaz.provouchers.cooldown;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownTiersTest {

    @Test
    void lowestHeldTierWins() {
        CooldownTiers tiers = new CooldownTiers(Map.of("vip", 0.5, "mvp", 0.0));
        Set<String> held = Set.of("provouchers.cooldown.vip", "provouchers.cooldown.mvp");
        assertThat(tiers.multiplier(held::contains)).isEqualTo(0.0);
    }

    @Test
    void unheldTiersDoNotApply() {
        CooldownTiers tiers = new CooldownTiers(Map.of("vip", 0.5));
        assertThat(tiers.multiplier(node -> false)).isEqualTo(1.0);
        assertThat(tiers.multiplier(Set.of("provouchers.cooldown.vip")::contains)).isEqualTo(0.5);
    }

    @Test
    void negativeMultipliersClampToZero() {
        CooldownTiers tiers = new CooldownTiers(Map.of("vip", -2.0));
        assertThat(tiers.multiplier(node -> true)).isEqualTo(0.0);
    }

    @Test
    void noTiersMeansFullCooldown() {
        assertThat(CooldownTiers.none().isEmpty()).isTrue();
        assertThat(CooldownTiers.none().multiplier(node -> true)).isEqualTo(1.0);
    }
}
