package so.alaz.provouchers.voucher;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FireworkSpecTest {

    @Test
    void parsesNamedAndHexColors() {
        FireworkSpec spec = FireworkSpec.of(List.of("RED", "#FF8800"), List.of("white"), "BALL", 0);
        assertThat(spec.colors()).containsExactly(Color.RED, Color.fromRGB(0xFF8800));
        assertThat(spec.fade()).containsExactly(Color.WHITE);
        assertThat(spec.type()).isEqualTo(FireworkEffect.Type.BALL);
    }

    @Test
    void fadeIsOptional() {
        FireworkSpec spec = FireworkSpec.of(List.of("LIME"), List.of(), "ball_large", 1);
        assertThat(spec.fade()).isEmpty();
        assertThat(spec.type()).isEqualTo(FireworkEffect.Type.BALL_LARGE);
        assertThat(spec.power()).isEqualTo(1);
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> FireworkSpec.of(List.of(), List.of(), "BALL", 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("color");
        assertThatThrownBy(() -> FireworkSpec.of(List.of("CHARTREUSE"), List.of(), "BALL", 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CHARTREUSE");
        assertThatThrownBy(() -> FireworkSpec.of(List.of("RED"), List.of(), "SPIRAL", 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shape");
        assertThatThrownBy(() -> FireworkSpec.of(List.of("RED"), List.of(), "BALL", 5))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("power");
    }
}
