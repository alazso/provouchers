package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSettingsTest {

    private static YamlConfiguration parse(String yaml) {
        return YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml));
    }

    @Test
    void readsTheShippedDefaults() throws Exception {
        try (InputStream in = LiveSettingsTest.class.getResourceAsStream("/config.yml")) {
            assertThat(in).isNotNull();
            YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
            LiveSettings settings = LiveSettings.from(shipped);
            assertThat(settings.removeOnDiscovery()).isTrue();
            assertThat(settings.warningEnabled()).isFalse();
            assertThat(settings.notifyEnabled()).isTrue();
            assertThat(settings.batchQuiet()).isTrue();
            assertThat(settings.confirmWindowSeconds()).isEqualTo(5);
            assertThat(settings.guiConfirm()).isFalse();
            assertThat(settings.leftClickPreview()).isFalse();
            assertThat(settings.stashRows()).isEqualTo(6);
            assertThat(settings.stashTitle()).contains("Your Stash");
        }
    }

    @Test
    void readsEditedValues() {
        LiveSettings settings = LiveSettings.from(parse("""
            anti-dupe:
              remove-on-discovery: false
              warning:
                enabled: true
                text: "<gold>copied"
              notify:
                enabled: false
            redeem:
              left-click-preview: true
              batch-open-quiet: false
              confirm-window-seconds: 30
              confirm-style: gui
            stash:
              title: "<red>Rewards"
              rows: 3
            """));
        assertThat(settings).isEqualTo(new LiveSettings(false, true, "<gold>copied", false, false, 30, true, true,
            "<red>Rewards", 3));
    }

    @Test
    void fallsBackToTheDefaultsOnAFileFromAnOlderVersion() {
        LiveSettings settings = LiveSettings.from(parse("storage:\n  backend: sqlite\n"));
        assertThat(settings.removeOnDiscovery()).isTrue();
        assertThat(settings.warningText()).isEqualTo("<red>This item has been duplicated");
        assertThat(settings.confirmWindowSeconds()).isEqualTo(5);
        assertThat(settings.stashRows()).isEqualTo(6);
    }

    @Test
    void readsTheConfirmStyleCaseInsensitively() {
        assertThat(LiveSettings.from(parse("redeem:\n  confirm-style: GUI\n")).guiConfirm()).isTrue();
        assertThat(LiveSettings.from(parse("redeem:\n  confirm-style: chat\n")).guiConfirm()).isFalse();
    }
}
