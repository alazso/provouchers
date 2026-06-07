package so.alaz.provouchers.voucher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkullSpecTest {

    @Test
    void resolvesSourcesAndAliases() {
        assertThat(SkullSpec.source("texture")).isEqualTo(SkullSpec.Source.TEXTURE);
        assertThat(SkullSpec.source("base64")).isEqualTo(SkullSpec.Source.TEXTURE);
        assertThat(SkullSpec.source("URL")).isEqualTo(SkullSpec.Source.URL);
        assertThat(SkullSpec.source("player")).isEqualTo(SkullSpec.Source.NAME);
        assertThat(SkullSpec.source("uuid")).isEqualTo(SkullSpec.Source.UUID);
    }

    @Test
    void rejectsUnknownSource() {
        assertThatThrownBy(() -> SkullSpec.source("hologram"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new SkullSpec(SkullSpec.Source.TEXTURE, "  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesUuidValue() {
        assertThat(new SkullSpec(SkullSpec.Source.UUID, "069a79f4-44e9-4726-a5be-fca90e38aaf5").value())
            .isNotBlank();
        assertThatThrownBy(() -> new SkullSpec(SkullSpec.Source.UUID, "not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textureValueKept() {
        SkullSpec spec = new SkullSpec(SkullSpec.Source.TEXTURE, "eyJ0ZXh0dXJlcyI6...");
        assertThat(spec.source()).isEqualTo(SkullSpec.Source.TEXTURE);
        assertThat(spec.value()).startsWith("eyJ0");
    }
}
