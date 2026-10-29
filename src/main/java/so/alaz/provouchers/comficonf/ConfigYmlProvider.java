package so.alaz.provouchers.comficonf;

import com.comficonf.api.ApplyContext;
import com.comficonf.api.ApplyResult;
import com.comficonf.api.ConfigPath;
import com.comficonf.api.ConfigProvider;
import com.comficonf.api.ConfigSnapshot;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.FieldChange;
import com.comficonf.api.ValidationResult;
import com.comficonf.api.bukkit.SectionMapper;
import com.comficonf.api.io.BackupPolicy;
import com.comficonf.api.io.RevisionConflictException;
import com.comficonf.api.io.Revisions;
import com.comficonf.api.io.SafeFileWriter;
import com.comficonf.api.schema.ApplyMode;
import com.comficonf.api.schema.ConfigSchema;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Exposes config.yml to ComfiConf. Callbacks run on a bridge worker thread; the only main-thread work, re-reading
 * config.yml into the plugin after a save, runs through {@link ApplyContext#callSync}.
 */
final class ConfigYmlProvider implements ConfigProvider {

    /** A § color code. ProVouchers renders these texts with MiniMessage, which refuses them with an exception. */
    private static final Pattern SECTION_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    /** An & color code. MiniMessage shows it as plain text. */
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&[0-9a-fk-orA-FK-OR]");

    /** MiniMessage texts of config.yml. */
    private static final List<String> MINI_MESSAGE_PATHS = List.of("anti-dupe.warning.text", "stash.title");

    /** Rows below this show as this many; see {@code StashGui}. */
    private static final int MIN_STASH_ROWS = 3;

    private final Path file;
    private final Path langDirectory;
    private final BackupPolicy backups;
    private final Logger logger;
    private final Callable<Boolean> reload;
    private final SectionMapper mapper = ConfigYmlSchema.mapper();
    private final ConfigValues startup;

    /**
     * @param file config.yml
     * @param backupDirectory where {@link SafeFileWriter} keeps the previous versions of config.yml
     * @param logger the plugin's logger
     * @param reload re-reads config.yml into the plugin on the server main thread; {@code true} when it loaded
     */
    ConfigYmlProvider(Path file, Path backupDirectory, Logger logger, Callable<Boolean> reload) {
        this.file = file;
        this.langDirectory = file.resolveSibling("lang");
        this.backups = BackupPolicy.in(backupDirectory);
        this.logger = logger;
        this.reload = reload;
        this.startup = readStartupValues();
    }

    @Override
    public ConfigSchema describe() {
        return mapper.schema();
    }

    /** Reads the file once; values and revision come from the same bytes, so edits by hand are detected. */
    @Override
    public ConfigSnapshot read() throws IOException, InvalidConfigurationException {
        byte[] content = Files.readAllBytes(file);
        return ConfigSnapshot.of(mapper.read(Yaml.parse(content)), Revisions.of(content));
    }

    /** Text config.yml cannot store unchanged, then the plugin's rules that the schema cannot express. */
    @Override
    public ValidationResult validate(ConfigValues proposed) {
        ValidationResult.Builder rules = ValidationResult.builder();
        for (String path : MINI_MESSAGE_PATHS) {
            String text = proposed.getString(path, "");
            if (SECTION_CODE.matcher(text).find()) {
                rules.error(path, "This text is MiniMessage, which cannot show § color codes. Use tags such as "
                    + "<red> instead.");
            } else if (AMPERSAND_CODE.matcher(text).find()) {
                rules.warning(path, "& color codes show as plain text here. Use MiniMessage tags such as <red>.");
            }
        }
        if (!"sqlite".equals(proposed.getString("storage.backend", "sqlite"))) {
            requireText(proposed, "storage.host", "Enter the host of the database server.", rules);
            requireText(proposed, "storage.database", "Enter the name of the database.", rules);
        }
        if (proposed.getLong("stash.rows", MIN_STASH_ROWS) < MIN_STASH_ROWS) {
            rules.warning("stash.rows", "The Stash menu has at least 3 rows, so this shows as 3.");
        }
        String locale = proposed.getString("locale.default", "");
        if (!locale.isEmpty() && !"en".equalsIgnoreCase(locale) && !hasLanguageFile(locale)) {
            rules.warning("locale.default", "The lang folder has no file for this language, so messages show in "
                + "English until one is added.");
        }
        return mapper.validate(proposed).merge(rules.build());
    }

    @Override
    public ApplyResult apply(ApplyContext context) throws IOException, InvalidConfigurationException {
        byte[] current = Files.readAllBytes(file);
        String currentRevision = Revisions.of(current);
        if (!currentRevision.equals(context.baseRevision())) {
            return ApplyResult.conflict(currentRevision);
        }
        ValidationResult storable = mapper.validate(context.proposed());
        if (!storable.isValid()) {
            return ApplyResult.rejected(storable);
        }
        YamlConfiguration yaml = Yaml.parse(current);
        mapper.write(context.proposed(), yaml);
        byte[] updated = Yaml.encodeLike(yaml.saveToString(), current);
        List<String> altered = alteredPaths(context.proposed(), Yaml.parse(updated));
        if (!altered.isEmpty()) {
            ValidationResult.Builder rejection = ValidationResult.builder();
            for (String path : altered) {
                rejection.error(path, "config.yml cannot store this value unchanged, so nothing was saved.");
            }
            return ApplyResult.rejected(rejection.build());
        }
        String newRevision;
        try {
            newRevision = SafeFileWriter.write(file, updated, context.baseRevision(), backups).newRevision();
        } catch (RevisionConflictException e) {
            return ApplyResult.conflict(e.currentRevision());
        }
        boolean loaded;
        try {
            loaded = Boolean.TRUE.equals(context.callSync(reload));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.log(Level.WARNING, "Saved config.yml from ComfiConf, but could not load it into ProVouchers", e);
            loaded = false;
        }
        if (!loaded) {
            return ApplyResult.savedRestartRequired(newRevision, "The values were saved, but ProVouchers could not "
                + "load them. They take effect after /voucher reload or a restart.");
        }
        logger.info("Applied a config.yml change from ComfiConf requested by " + context.actor().label());
        List<String> waiting = restartPending(context);
        if (!waiting.isEmpty()) {
            return ApplyResult.savedRestartRequired(newRevision, "Saved. Settings marked Restart required take "
                + "effect after a server restart; all other changes are active.", waiting);
        }
        return ApplyResult.applied(newRevision);
    }

    private static void requireText(ConfigValues proposed, String path, String message,
                                    ValidationResult.Builder rules) {
        if (proposed.getString(path, "").isBlank()) {
            rules.error(path, message);
        }
    }

    /** Whether lang/ holds a file for {@code locale}, matched as ProVouchers matches it: by lower-cased name. */
    private boolean hasLanguageFile(String locale) {
        String wanted = locale.toLowerCase(Locale.ROOT) + ".yml";
        try (Stream<Path> files = Files.list(langDirectory)) {
            return files.anyMatch(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).equals(wanted));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Changed restart-only paths whose new value differs from the value in effect since startup. */
    private List<String> restartPending(ApplyContext context) {
        if (!context.requiresRestart()) {
            return List.of();
        }
        List<String> waiting = new ArrayList<>();
        for (FieldChange change : context.changes()) {
            if (change.applyMode() == ApplyMode.RESTART
                && !Objects.equals(context.proposed().get(change.path()), startup.get(change.path()))) {
                waiting.add(change.path());
            }
        }
        return waiting;
    }

    /**
     * Values of the file at registration, which follows the load of config.yml in {@code onEnable}: the values the
     * plugin's restart-only consumers use. Empty when unreadable, so every restart-only change is reported.
     */
    private ConfigValues readStartupValues() {
        try {
            return mapper.read(Yaml.parse(Files.readAllBytes(file)));
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            return ConfigValues.empty();
        }
    }

    /** Paths whose value in the saved document differs from the proposal; missing and null count as equal. */
    private List<String> alteredPaths(ConfigValues proposed, YamlConfiguration saved) {
        List<String> altered = new ArrayList<>();
        try {
            collectAltered(proposed.asMap(), mapper.read(saved).asMap(), "", altered);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "config.yml would not read back the values from ComfiConf", e);
            altered.add("");
        }
        return altered;
    }

    private static void collectAltered(Object expected, Object saved, String path, List<String> altered) {
        if (Objects.equals(expected, saved)) {
            return;
        }
        if (expected instanceof Map<?, ?> left && saved instanceof Map<?, ?> right) {
            Set<Object> keys = new LinkedHashSet<>(left.keySet());
            keys.addAll(right.keySet());
            for (Object key : keys) {
                collectAltered(left.get(key), right.get(key), ConfigPath.child(path, (String) key), altered);
            }
        } else if (expected instanceof List<?> left && saved instanceof List<?> right && left.size() == right.size()) {
            for (int i = 0; i < left.size(); i++) {
                collectAltered(left.get(i), right.get(i), ConfigPath.index(path, i), altered);
            }
        } else {
            altered.add(path);
        }
    }
}
