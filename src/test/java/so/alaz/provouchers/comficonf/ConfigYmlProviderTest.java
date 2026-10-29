package so.alaz.provouchers.comficonf;

import com.comficonf.api.ApplyResult;
import com.comficonf.api.ApplyStatus;
import com.comficonf.api.ConfigSnapshot;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.ValidationIssue;
import com.comficonf.api.ValidationResult;
import com.comficonf.api.io.BackupPolicy;
import com.comficonf.api.io.Revisions;
import com.comficonf.api.io.SafeFileWriter;
import com.comficonf.api.schema.ApplyMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigYmlProviderTest {

    @TempDir
    Path dataFolder;

    private Path file;
    private Path backups;
    /** The plugin's loaded copy of config.yml, as {@code getConfig()} returns it after a reload. */
    private final AtomicReference<YamlConfiguration> loaded = new AtomicReference<>();
    private final AtomicInteger reloads = new AtomicInteger();
    private ConfigYmlProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        file = dataFolder.resolve("config.yml");
        backups = dataFolder.resolve("backups");
        Files.write(file, ConfigYmlMappingTest.shippedConfig());
        Files.createDirectories(dataFolder.resolve("lang"));
        Files.writeString(dataFolder.resolve("lang").resolve("de.yml"), "prefix: \"\"\n");
        loaded.set(YamlConfiguration.loadConfiguration(file.toFile()));
        provider = providerReloadingWith(() -> {
            reloads.incrementAndGet();
            loaded.set(YamlConfiguration.loadConfiguration(file.toFile()));
            return true;
        });
    }

    private ConfigYmlProvider providerReloadingWith(Callable<Boolean> reload) {
        return new ConfigYmlProvider(file, backups, Logger.getLogger("test"), reload);
    }

    @Test
    void readsValuesAndTheRevisionOfTheSameBytes() throws Exception {
        ConfigSnapshot snapshot = provider.read();
        assertThat(snapshot.revision()).isEqualTo(Revisions.of(Files.readAllBytes(file)));
        assertThat(snapshot.values().getString("stash.overflow")).isEqualTo("drop");
        Files.writeString(file, Files.readString(file) + "\n# edited by hand\n");
        assertThat(provider.read().revision()).isNotEqualTo(snapshot.revision());
    }

    @Test
    void appliesALiveChangeKeepsCommentsAndUndeclaredDataAndActivatesIt() throws Exception {
        ConfigSnapshot current = provider.read();
        TestApplyContext context = TestApplyContext.changing(current.revision(), current.values(), "stash.overflow",
            "stash", ApplyMode.LIVE);

        ApplyResult result = provider.apply(context);

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        byte[] written = Files.readAllBytes(file);
        assertThat(result.newRevision()).isEqualTo(Revisions.of(written));
        assertThat(new String(written, StandardCharsets.UTF_8))
            .contains("overflow: stash")
            .contains("# Backend: sqlite | mysql | mariadb | postgres")
            .contains("# cooldown:")
            .contains("#    vip: 0.5")
            .contains("rows: 6 # menu height, 1 to 6");
        assertThat(context.syncCalls).hasValue(1);
        assertThat(reloads).hasValue(1);
        assertThat(loaded.get().getString("stash.overflow")).isEqualTo("stash");
        assertThat(SafeFileWriter.listBackups(file, BackupPolicy.in(backups))).hasSize(1);
    }

    @Test
    void savesAFileWrittenWithCarriageReturnsWithCarriageReturns() throws Exception {
        Files.write(file, withCarriageReturns(ConfigYmlMappingTest.shippedConfig()));
        ConfigSnapshot current = provider.read();

        ApplyResult result = provider.apply(TestApplyContext.changing(current.revision(), current.values(),
            "stash.overflow", "stash", ApplyMode.LIVE));

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        String written = Files.readString(file);
        assertThat(written).contains("overflow: stash");
        assertThat(written.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void savesAFileWrittenWithLineFeedsWithLineFeeds() throws Exception {
        Files.write(file, withLineFeeds(ConfigYmlMappingTest.shippedConfig()));
        ConfigSnapshot current = provider.read();

        ApplyResult result = provider.apply(TestApplyContext.changing(current.revision(), current.values(),
            "stash.overflow", "stash", ApplyMode.LIVE));

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(Files.readString(file)).contains("overflow: stash").doesNotContain("\r");
    }

    @Test
    void readsBackTheValuesOfAFileSavedWithCarriageReturns() throws Exception {
        Files.write(file, withCarriageReturns(ConfigYmlMappingTest.shippedConfig()));
        ConfigSnapshot current = provider.read();

        provider.apply(TestApplyContext.changing(current.revision(), current.values(), "stash.rows", 4L,
            ApplyMode.LIVE));

        assertThat(loaded.get().getInt("stash.rows")).isEqualTo(4);
        assertThat(loaded.get().getString("stash.overflow")).isEqualTo("drop");
    }

    @Test
    void reportsAConflictAndWritesNothingWhenTheFileChanged() throws Exception {
        ConfigSnapshot current = provider.read();
        Files.writeString(file, Files.readString(file) + "\n# edited by hand\n");
        byte[] edited = Files.readAllBytes(file);

        TestApplyContext context = TestApplyContext.changing(current.revision(), current.values(), "stash.overflow",
            "stash", ApplyMode.LIVE);
        ApplyResult result = provider.apply(context);

        assertThat(result.status()).isEqualTo(ApplyStatus.CONFLICT);
        assertThat(result.currentRevision()).isEqualTo(Revisions.of(edited));
        assertThat(Files.readAllBytes(file)).isEqualTo(edited);
        assertThat(SafeFileWriter.listBackups(file, BackupPolicy.in(backups))).isEmpty();
        assertThat(context.syncCalls).hasValue(0);
    }

    @Test
    void neverReportsAppliedWhenTheFileDoesNotLoad() throws Exception {
        ConfigYmlProvider failing = providerReloadingWith(() -> false);
        ConfigSnapshot current = failing.read();

        ApplyResult result = failing.apply(TestApplyContext.changing(current.revision(), current.values(),
            "stash.overflow", "stash", ApplyMode.LIVE));

        assertThat(result.status()).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
        assertThat(result.persisted()).isTrue();
        assertThat(result.activated()).isFalse();
        assertThat(result.newRevision()).isEqualTo(Revisions.of(Files.readAllBytes(file)));
    }

    @Test
    void neverReportsAppliedWhenTheReloadThrows() throws Exception {
        ConfigYmlProvider failing = providerReloadingWith(() -> {
            throw new IllegalStateException("reload failed");
        });
        ConfigSnapshot current = failing.read();

        ApplyResult result = failing.apply(TestApplyContext.changing(current.revision(), current.values(),
            "stash.overflow", "stash", ApplyMode.LIVE));

        assertThat(result.status()).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
        assertThat(Files.readString(file)).contains("overflow: stash");
    }

    @Test
    void keepsARestartOnlyChangeWaiting() throws Exception {
        ConfigSnapshot current = provider.read();

        ApplyResult result = provider.apply(TestApplyContext.changing(current.revision(), current.values(),
            "stash.rows", 4L, ApplyMode.RESTART));

        assertThat(result.status()).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
        assertThat(result.restartRequired()).containsExactly("stash.rows");
        assertThat(Files.readString(file)).contains("rows: 4");
        assertThat(reloads).hasValue(1);
    }

    @Test
    void reportsRestoringTheStartupValueAsApplied() throws Exception {
        ConfigSnapshot first = provider.read();
        provider.apply(TestApplyContext.changing(first.revision(), first.values(), "stash.rows", 4L,
            ApplyMode.RESTART));
        ConfigSnapshot second = provider.read();

        ApplyResult result = provider.apply(TestApplyContext.changing(second.revision(), second.values(),
            "stash.rows", 6L, ApplyMode.RESTART));

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(Files.readString(file)).contains("rows: 6");
    }

    @Test
    void writesNumbersAsNumbers() throws Exception {
        ConfigSnapshot current = provider.read();

        provider.apply(TestApplyContext.changing(current.revision(), current.values(),
            "redeem.confirm-window-seconds", 10L, ApplyMode.RESTART));

        assertThat(new String(withLineFeeds(Files.readAllBytes(file)), StandardCharsets.UTF_8))
            .contains("confirm-window-seconds: 10\n").doesNotContain("10.0");
        assertThat(loaded.get().getLong("redeem.confirm-window-seconds")).isEqualTo(10L);
    }

    @Test
    void refusesTextYamlCannotStoreAndWritesNothing() throws Exception {
        ConfigSnapshot current = provider.read();
        byte[] before = Files.readAllBytes(file);

        TestApplyContext context = TestApplyContext.changing(current.revision(), current.values(), "stash.title",
            "\u001B[31mStash", ApplyMode.RESTART);
        ApplyResult result = provider.apply(context);

        assertThat(result.status()).isEqualTo(ApplyStatus.REJECTED);
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        assertThat(SafeFileWriter.listBackups(file, BackupPolicy.in(backups))).isEmpty();
        assertThat(context.syncCalls).hasValue(0);
    }

    @Test
    void appliesUnchangedValuesToTheSameValues() throws Exception {
        ConfigSnapshot current = provider.read();

        ApplyResult result = provider.apply(new TestApplyContext(current.revision(), current.values(),
            current.values(), List.of()));

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(provider.read().values()).isEqualTo(current.values());
    }

    @Test
    void replacesTheWriteOnlyPasswordAndKeepsItOutOfTheResult() throws Exception {
        ConfigSnapshot current = provider.read();

        ApplyResult result = provider.apply(TestApplyContext.changing(current.revision(), current.values(),
            "storage.password", "n3w-secret", ApplyMode.RESTART));

        assertThat(result.status()).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
        assertThat(Files.readString(file)).contains("password: n3w-secret");
        assertThat(result.toString()).doesNotContain("n3w-secret");
        assertThat(result.message()).doesNotContain("n3w-secret");
    }

    @Test
    void acceptsTheShippedValuesWithoutIssues() throws Exception {
        assertThat(provider.validate(provider.read().values()).issues()).isEmpty();
    }

    @Test
    void refusesSectionColorCodesAndWarnsAboutAmpersandCodes() throws Exception {
        ConfigValues values = provider.read().values();

        ValidationResult section = provider.validate(values.with("stash.title", "§6Your Stash"));
        ValidationResult ampersand = provider.validate(values.with("anti-dupe.warning.text", "&cDuplicated"));

        assertThat(section.isValid()).isFalse();
        assertThat(section.errors()).extracting(ValidationIssue::path).containsExactly("stash.title");
        assertThat(ampersand.isValid()).isTrue();
        assertThat(ampersand.warnings()).extracting(ValidationIssue::path).containsExactly("anti-dupe.warning.text");
    }

    @Test
    void needsAHostAndDatabaseForANetworkedBackend() throws Exception {
        ConfigValues values = provider.read().values().with("storage.host", " ").with("storage.database", "");

        assertThat(provider.validate(values).isValid()).isTrue();
        ValidationResult mysql = provider.validate(values.with("storage.backend", "mysql"));
        assertThat(mysql.errors()).extracting(ValidationIssue::path)
            .containsExactly("storage.host", "storage.database");
    }

    @Test
    void warnsThatOneAndTwoStashRowsShowAsThree() throws Exception {
        ValidationResult result = provider.validate(provider.read().values().with("stash.rows", 2L));

        assertThat(result.isValid()).isTrue();
        assertThat(result.warnings()).extracting(ValidationIssue::path).containsExactly("stash.rows");
    }

    @Test
    void warnsAboutADefaultLanguageWithoutAFile() throws Exception {
        ConfigValues values = provider.read().values();

        assertThat(provider.validate(values.with("locale.default", "de")).issues()).isEmpty();
        assertThat(provider.validate(values.with("locale.default", "EN")).issues()).isEmpty();
        ValidationResult missing = provider.validate(values.with("locale.default", "xx"));
        assertThat(missing.isValid()).isTrue();
        assertThat(missing.warnings()).extracting(ValidationIssue::path).containsExactly("locale.default");
    }

    @Test
    void refusesAFileThatIsNotUtf8AndLeavesItAlone() throws Exception {
        byte[] legacy = new String(ConfigYmlMappingTest.shippedConfig(), StandardCharsets.UTF_8)
            .replace("overflow: drop", "overflow: drop # Beh\u00e4lter")
            .getBytes(Charset.forName("windows-1252"));
        Files.write(file, legacy);

        assertThatThrownBy(() -> provider.read()).isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(legacy);
    }

    private static byte[] withCarriageReturns(byte[] content) {
        return withText(content, text -> text.replace("\r\n", "\n").replace("\n", "\r\n"));
    }

    private static byte[] withLineFeeds(byte[] content) {
        return withText(content, text -> text.replace("\r\n", "\n"));
    }

    private static byte[] withText(byte[] content, UnaryOperator<String> change) {
        return change.apply(new String(content, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }
}
