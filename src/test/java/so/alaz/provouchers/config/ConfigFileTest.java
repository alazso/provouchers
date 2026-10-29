package so.alaz.provouchers.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The check a reload makes before it replaces the settings in effect. */
class ConfigFileTest {

    @TempDir
    Path folder;

    @Test
    void acceptsTheShippedFile() throws Exception {
        Path file = folder.resolve("config.yml");
        try (InputStream in = ConfigFileTest.class.getResourceAsStream("/config.yml")) {
            assertThat(in).isNotNull();
            Files.write(file, in.readAllBytes());
        }

        assertThat(ConfigFile.unreadableReason(file.toFile())).isNull();
    }

    @Test
    void acceptsAnEmptyFile() throws Exception {
        Path file = Files.writeString(folder.resolve("config.yml"), "");

        assertThat(ConfigFile.unreadableReason(file.toFile())).isNull();
    }

    @Test
    void reportsAFileThatDoesNotParse() throws Exception {
        Path file = Files.writeString(folder.resolve("config.yml"), "stash:\n\trows: 6\n");

        assertThat(ConfigFile.unreadableReason(file.toFile())).isNotNull();
    }

    @Test
    void reportsAMissingFile() {
        assertThat(ConfigFile.unreadableReason(new File(folder.toFile(), "absent.yml"))).isNotNull();
    }
}
