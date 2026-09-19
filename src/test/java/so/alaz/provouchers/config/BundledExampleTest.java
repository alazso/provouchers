package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Parses the example voucher and code files bundled in the plugin jar. */
class BundledExampleTest {

    @Test
    void exampleVoucherParses() throws Exception {
        assertThatCode(() -> VoucherParser.parseVoucher(load("/vouchers/example.yml"), "example"))
            .doesNotThrowAnyException();
    }

    @Test
    void exampleCodeParses() throws Exception {
        assertThatCode(() -> VoucherParser.parseCode(load("/codes/example.yml"), "example"))
            .doesNotThrowAnyException();
    }

    private static YamlConfiguration load(String resource) throws Exception {
        try (InputStream in = BundledExampleTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("bundled resource %s", resource).isNotNull();
            return YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
