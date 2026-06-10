package so.alaz.provouchers.locale;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves plugin messages from language files, so every player-facing string is translatable.
 *
 * <p>The bundled {@code lang/en.yml} is the ground truth and the ultimate fallback: any key a
 * translation omits resolves to English, so a partial translation never leaves a blank. Server
 * owners add {@code lang/<code>.yml} files in the data folder. When {@code per-player} is on, a
 * message is served in the viewer's Minecraft client language if a matching file exists, else the
 * configured default; console and other viewer-less output always use the default.
 *
 * <p>{@link #get} returns a MiniMessage string with {@code {prefix}} and message placeholders
 * substituted; the caller renders it (so PlaceholderAPI still applies for that viewer).
 */
public final class Messages {

    private final File langDir;
    private final String defaultLocale;
    private final boolean perPlayer;

    /** Flattened bundled English (classpath), the always-present fallback. */
    private final Map<String, String> bundled;

    /** locale code (lower case) to its flattened key/template map; replaced wholesale on reload. */
    private volatile Map<String, Map<String, String>> languages = Map.of();

    public Messages(File dataFolder, String defaultLocale, boolean perPlayer) {
        this.langDir = new File(dataFolder, "lang");
        this.defaultLocale = defaultLocale == null || defaultLocale.isBlank()
            ? "en" : defaultLocale.toLowerCase(Locale.ROOT);
        this.perPlayer = perPlayer;
        this.bundled = loadBundled();
        reload();
    }

    /** Reloads every {@code lang/<code>.yml} from disk. Picks up edited and newly added translations. */
    public void reload() {
        Map<String, Map<String, String>> loaded = new HashMap<>();
        File[] files = langDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String code = name.substring(0, name.length() - ".yml".length()).toLowerCase(Locale.ROOT);
                loaded.put(code, flatten(YamlConfiguration.loadConfiguration(file)));
            }
        }
        this.languages = loaded;
    }

    /** The locale codes that have a loaded lang file (English is always available as a fallback). */
    public java.util.Set<String> loadedLanguages() {
        return java.util.Set.copyOf(languages.keySet());
    }

    /**
     * The message for {@code key}, in {@code viewer}'s language, with {@code {prefix}} and the given
     * {@code placeholders} (alternating name, value) filled in. Never null; an unknown key returns a
     * visible {@code <red>missing:key} marker rather than throwing.
     */
    public String get(@Nullable Player viewer, String key, Object... placeholders) {
        return getForLocale(viewer == null ? null : viewer.locale(), key, placeholders);
    }

    /** Resolves by locale directly. The {@link #get(Player, String, Object...)} entry point delegates here. */
    String getForLocale(@Nullable Locale locale, String key, Object... placeholders) {
        List<Map<String, String>> chain = resolutionChain(locale);
        String template = lookup(chain, key);
        if (template == null) {
            return "<red>missing:" + key;
        }
        return applyPrefix(chain, template, placeholders);
    }

    /**
     * Substitutes {@code {prefix}} (in the viewer's language) and placeholders into an arbitrary
     * template, e.g. a per-voucher message given inline rather than looked up by key.
     */
    public String format(@Nullable Player viewer, String template, Object... placeholders) {
        List<Map<String, String>> chain = resolutionChain(viewer == null ? null : viewer.locale());
        return applyPrefix(chain, template, placeholders);
    }

    private static String applyPrefix(List<Map<String, String>> chain, String template, Object... placeholders) {
        String prefix = lookup(chain, "prefix");
        String out = template.replace("{prefix}", prefix == null ? "" : prefix);
        return fill(out, placeholders);
    }

    /** Substitutes {@code {name}} placeholders from alternating name/value pairs. No prefix handling. */
    public static String fill(String template, Object... placeholders) {
        String out = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            out = out.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return out;
    }

    /** Maps to look in, most specific first, always ending at the bundled English fallback. */
    private List<Map<String, String>> resolutionChain(@Nullable Locale locale) {
        List<Map<String, String>> chain = new ArrayList<>(3);
        Map<String, Map<String, String>> langs = languages;
        if (perPlayer && locale != null) {
            Map<String, String> exact = langs.get(locale.toString().toLowerCase(Locale.ROOT).replace('-', '_'));
            if (exact != null) {
                chain.add(exact);
            }
            Map<String, String> language = langs.get(locale.getLanguage().toLowerCase(Locale.ROOT));
            if (language != null && language != exact) {
                chain.add(language);
            }
        }
        Map<String, String> def = langs.get(defaultLocale);
        if (def != null && !chain.contains(def)) {
            chain.add(def);
        }
        chain.add(bundled);
        return chain;
    }

    @Nullable
    private static String lookup(List<Map<String, String>> chain, String key) {
        for (Map<String, String> map : chain) {
            String value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> loadBundled() {
        try (InputStream in = getClass().getResourceAsStream("/lang/en.yml")) {
            if (in == null) {
                return Map.of();
            }
            return flatten(YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            return Map.of();
        }
    }

    /** Flattens a YAML tree to dotted leaf keys ({@code command.give.success}) mapped to their strings. */
    private static Map<String, String> flatten(FileConfiguration config) {
        Map<String, String> flat = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                flat.put(key, config.getString(key));
            }
        }
        return flat;
    }
}
