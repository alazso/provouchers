package so.alaz.provouchers.voucher;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherModelTest {

    private static Voucher voucher(String id, long cooldown) {
        return new Voucher(id, id, List.of(), new VoucherItem("PAPER", null, null, false, null, Map.of()),
            List.of(), List.of(), List.of(), Map.of(), false, false, cooldown, -1, -1, null, null,
            false, true, false, false, null, null, null);
    }

    @Test
    void blankItemMaterialRejected() {
        assertThatThrownBy(() -> new VoucherItem("  ", null, null, false, null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeCooldownRejected() {
        assertThatThrownBy(() -> voucher("a", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasRewardsReflectsContent() {
        assertThat(voucher("a", 0).hasRewards()).isFalse();
    }

    @Test
    void codeKeyHonoursCaseSensitivity() {
        VoucherCode sensitive = code("AbC", true);
        VoucherCode insensitive = code("AbC", false);
        assertThat(sensitive.key()).isEqualTo("AbC");
        assertThat(insensitive.key()).isEqualTo("abc");
        assertThat(insensitive.matches("ABC")).isTrue();
        assertThat(sensitive.matches("abc")).isFalse();
        assertThat(sensitive.matches("AbC")).isTrue();
    }

    @Test
    void globalLimitFlag() {
        assertThat(code("x", false).hasGlobalLimit()).isFalse();
        VoucherCode capped = new VoucherCode("y", false, 5, 1, null, null,
            List.of(), List.of(), List.of(), Map.of(), false);
        assertThat(capped.hasGlobalLimit()).isTrue();
    }

    @Test
    void registryLooksUpVouchersCaseInsensitivelyAndFindsCodes() {
        VoucherRegistry registry = new VoucherRegistry();
        registry.register(voucher("Daily", 0));
        registry.register(code("SECRET", true));
        assertThat(registry.getVoucher("daily")).isPresent();
        assertThat(registry.getVoucher("missing")).isEmpty();
        assertThat(registry.findCode("SECRET")).isNotNull();
        assertThat(registry.findCode("secret")).isNull();
        assertThat(registry.voucherCount()).isEqualTo(1);
        assertThat(registry.codeCount()).isEqualTo(1);
        registry.clear();
        assertThat(registry.voucherCount()).isZero();
    }

    private static VoucherCode code(String value, boolean caseSensitive) {
        return new VoucherCode(value, caseSensitive, -1, 1, null, null,
            List.<Map<String, Object>>of(), List.of(), List.of(), Map.of(), false);
    }
}
