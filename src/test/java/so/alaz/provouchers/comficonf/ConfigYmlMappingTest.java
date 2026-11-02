package so.alaz.provouchers.comficonf;

import com.comficonf.api.ConfigValues;
import com.comficonf.api.bukkit.SectionMapper;
import com.comficonf.api.schema.ApplyMode;
import com.comficonf.api.schema.ConfigField;
import com.comficonf.api.schema.ConfigSchema;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The schema of config.yml against the shipped file and the file of the first release. */
class ConfigYmlMappingTest {

    private static final SectionMapper MAPPER = ConfigYmlSchema.mapper();

    /** config.yml as ProVouchers 0.1.0 shipped it; servers that started on it can still have this file. */
    static final String CONFIG_0_1_0 = """
        # ProVouchers configuration.

        storage:
          # Backend: sqlite | mysql | mariadb | postgres
          # SQLite is file-based and needs no extra setup; the others connect to a server.
          backend: sqlite
          # The settings below are used only by the networked backends.
          host: localhost
          port: 5432
          database: provouchers
          username: provouchers
          password: ""
          pool-size: 10

        anti-dupe:
          # When a duplicate voucher is detected on redeem, remove the item instead of
          # returning it to the player.
          remove-on-discovery: true
        """;

    static byte[] shippedConfig() throws IOException {
        try (InputStream in = ConfigYmlMappingTest.class.getResourceAsStream("/config.yml")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void buildsTheSchema() {
        assertThat(MAPPER.schema().fieldCount()).isBetween(1, ConfigSchema.MAX_FIELDS);
    }

    @Test
    void marksTheSettingsReadOnEveryUseAsLive() {
        List<String> live = new ArrayList<>();
        collectLive(MAPPER.schema().fields(), "", live);
        assertThat(live).containsExactlyInAnyOrder(
            "anti-dupe.remove-on-discovery",
            "anti-dupe.warning.enabled",
            "anti-dupe.warning.text",
            "anti-dupe.notify.enabled",
            "redeem.left-click-preview",
            "redeem.block-entity-use",
            "redeem.batch-open-quiet",
            "redeem.confirm-window-seconds",
            "redeem.confirm-style",
            "stash.title",
            "stash.rows",
            "stash.overflow");
    }

    @Test
    void readsTheShippedFileAndAcceptsIt() throws Exception {
        ConfigValues values = MAPPER.read(Yaml.parse(shippedConfig()));
        assertThat(values.getString("storage.backend")).isEqualTo("sqlite");
        assertThat(values.getStringList("command-aliases")).containsExactly("vouchers");
        assertThat(values.getLong("stash.rows")).isEqualTo(6L);
        assertThat(values.get("storage.password")).isNull();
        assertThat(values.get("stash.expire-after")).isNull();
        assertThat(SchemaConformance.problems(MAPPER.schema(), values)).isEmpty();
        assertThat(MAPPER.validate(values).isValid()).isTrue();
    }

    @Test
    void acceptsTheFileOfTheFirstRelease() throws Exception {
        ConfigValues values = MAPPER.read(Yaml.parse(CONFIG_0_1_0.getBytes(StandardCharsets.UTF_8)));
        assertThat(values.get("locale.default")).isNull();
        assertThat(values.get("stash.enabled")).isNull();
        assertThat(SchemaConformance.problems(MAPPER.schema(), values)).isEmpty();
    }

    @Test
    void roundTripsTheShippedFileWithoutChangingTheValues() throws Exception {
        assertRoundTrip(shippedConfig());
    }

    @Test
    void roundTripsTheFileOfTheFirstReleaseWithoutAddingKeys() throws Exception {
        YamlConfiguration yaml = assertRoundTrip(CONFIG_0_1_0.getBytes(StandardCharsets.UTF_8));
        assertThat(yaml.getKeys(false)).containsExactly("storage", "anti-dupe");
        assertThat(yaml.getConfigurationSection("anti-dupe").getKeys(false)).containsExactly("remove-on-discovery");
    }

    private static YamlConfiguration assertRoundTrip(byte[] content) throws Exception {
        YamlConfiguration yaml = Yaml.parse(content);
        String before = yaml.saveToString();
        ConfigValues values = MAPPER.read(yaml);
        MAPPER.write(values, yaml);
        assertThat(yaml.saveToString()).isEqualTo(before);
        assertThat(MAPPER.read(Yaml.parse(yaml.saveToString().getBytes(StandardCharsets.UTF_8)))).isEqualTo(values);
        return yaml;
    }

    private static void collectLive(List<ConfigField> fields, String parent, List<String> live) {
        for (ConfigField field : fields) {
            String path = parent.isEmpty() ? field.key() : parent + "." + field.key();
            if (!field.fields().isEmpty()) {
                collectLive(field.fields(), path, live);
            } else if (field.applyMode() == ApplyMode.LIVE) {
                live.add(path);
            }
        }
    }
}
