package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the bundled example files against drifting from the parser: they ship to every
 * server and load on first start, so an invalid key would surface as a reload error for
 * users. Loads each example and parses it the way {@link ConfigManager} does.
 */
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
