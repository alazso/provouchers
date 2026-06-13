package so.alaz.provouchers.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.util.LegacyText;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports CrazyVouchers configs into ProVouchers voucher files: the modern
 * one-voucher-per-file layout ({@code plugins/CrazyVouchers/vouchers/*.yml}, a root
 * {@code voucher:} section) and the legacy single {@code vouchers.yml} with named
 * sections. Existing ProVouchers ids are never overwritten. Text converts from legacy
 * color codes and curly tokens; keys with no ProVouchers equivalent are reported, not
 * silently dropped.
 */
public final class CrazyVouchersImporter {

    /** The outcome of one import run. */
    public record Result(List<String> imported, List<String> skipped, List<String> warnings,
                         boolean sourceFound) {
    }

    private final File pluginsDir;
    private final File vouchersDir;
    private final VoucherRegistry registry;

    public CrazyVouchersImporter(File dataFolder, VoucherRegistry registry) {
        this.pluginsDir = dataFolder.getParentFile();
        this.vouchersDir = new File(dataFolder, "vouchers");
        this.registry = registry;
    }

    /** Runs the import. The caller reloads the registry afterwards when anything imported. */
    public Result importAll() {
        File source = new File(pluginsDir, "CrazyVouchers");
        if (!source.isDirectory()) {
            return new Result(List.of(), List.of(), List.of(), false);
        }
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        File perFileDir = new File(source, "vouchers");
        File[] files = perFileDir.isDirectory()
            ? perFileDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"))
            : null;
        if (files != null) {
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4);
                ConfigurationSection voucher =
                    YamlConfiguration.loadConfiguration(file).getConfigurationSection("voucher");
                if (voucher == null) {
                    skipped.add(name + ": no 'voucher' section");
                    continue;
                }
                importOne(name, voucher, imported, skipped, warnings);
            }
        }
        File legacy = new File(source, "vouchers.yml");
        if (legacy.isFile()) {
            ConfigurationSection root =
                YamlConfiguration.loadConfiguration(legacy).getConfigurationSection("vouchers");
            if (root != null) {
                for (String name : root.getKeys(false)) {
                    ConfigurationSection voucher = root.getConfigurationSection(name);
                    if (voucher != null) {
                        importOne(name, voucher, imported, skipped, warnings);
                    }
                }
            }
        }
        return new Result(imported, skipped, warnings, true);
    }

    private void importOne(String name, ConfigurationSection voucher,
                           List<String> imported, List<String> skipped, List<String> warnings) {
        String id = sanitizeId(name);
        File target = new File(vouchersDir, id + ".yml");
        if (target.isFile() || registry.getVoucher(id).isPresent()) {
            skipped.add(id + ": a ProVouchers voucher with this id already exists");
            return;
        }
        Map<String, Object> data;
        try {
            data = convert(voucher, warnings, id);
        } catch (RuntimeException ex) {
            skipped.add(id + ": " + ex.getMessage());
            return;
        }
        YamlConfiguration out = new YamlConfiguration();
        out.options().setHeader(List.of(
            "Imported from CrazyVouchers by /voucher import.",
            "Review the result, then apply edits with /voucher reload " + id + "."));
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            out.set(entry.getKey(), entry.getValue());
        }
        try {
            out.save(target);
            imported.add(id);
        } catch (IOException ex) {
            skipped.add(id + ": could not write file (" + ex.getMessage() + ")");
        }
    }

    /** Maps one CrazyVouchers voucher section to ProVouchers keys, noting what has no mapping. */
    private Map<String, Object> convert(ConfigurationSection v, List<String> warnings, String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        String name = v.getString("name");
        if (name != null) {
            out.put("display-name", LegacyText.toMiniMessage(name));
        }
        List<String> lore = v.getStringList("lore");
        if (!lore.isEmpty()) {
            out.put("lore", lore.stream().map(LegacyText::toMiniMessage).toList());
        }

        out.put("item.material", v.getString("item", "PAPER").toUpperCase(Locale.ROOT));
        if (v.getBoolean("glowing", false)) {
            out.put("item.glow", true);
        }
        String texture = v.getString("player");
        if (texture != null && !texture.isBlank()) {
            out.put("item.skull.source", "url");
            out.put("item.skull.value", textureUrl(texture));
        }
        String hdb = v.getString("skull");
        if (hdb != null && !hdb.isBlank()) {
            out.put("item.custom", "hdb:" + hdb);
        }
        if (v.isInt("custom-model-data")) {
            out.put("item.custom-model-data", v.getInt("custom-model-data"));
        }
        if (v.getBoolean("has-argument", false)) {
            out.put("has-argument", true);
        }

        List<String> rewards = new ArrayList<>();
        for (String command : v.getStringList("commands")) {
            rewards.add("command: " + LegacyText.toMiniMessage(command));
        }
        String message = v.getString("options.message");
        if (message != null && !message.isBlank()) {
            rewards.add("message: " + LegacyText.toMiniMessage(message));
        }

        // The give-items DSL becomes defined items granted by @ references.
        Map<String, Object> definedItems = new LinkedHashMap<>();
        int itemIndex = 0;
        for (String dsl : v.getStringList("items")) {
            itemIndex++;
            String key = "imported_" + itemIndex;
            Map<String, Object> defined = convertItemDsl(dsl, warnings, id);
            Object amount = defined.remove("__amount");
            definedItems.put(key, defined);
            rewards.add("item: @" + key + (amount != null ? " " + amount : ""));
        }
        if (!definedItems.isEmpty()) {
            out.put("items", definedItems);
        }
        if (!rewards.isEmpty()) {
            out.put("rewards", rewards);
        }

        List<String> randomCommands = v.getStringList("random-commands");
        if (!randomCommands.isEmpty()) {
            List<Map<String, Object>> sets = new ArrayList<>();
            for (String command : randomCommands) {
                sets.add(Map.of("weight", 1,
                    "rewards", List.of("command: " + LegacyText.toMiniMessage(command))));
            }
            out.put("random-rewards", sets);
        }

        if (v.getBoolean("options.sound.toggle", false)) {
            List<String> sounds = v.getStringList("options.sound.sounds");
            if (!sounds.isEmpty()) {
                out.put("effects.sound", sounds.get(0).toLowerCase(Locale.ROOT)
                    + " " + v.getDouble("options.sound.volume", 1.0)
                    + " " + v.getDouble("options.sound.pitch", 1.0));
                if (sounds.size() > 1) {
                    warnings.add(id + ": only the first of " + sounds.size() + " sounds was imported");
                }
            }
        }
        if (v.getBoolean("options.firework.toggle", false)) {
            String colors = v.getString("options.firework.colors", "");
            if (!colors.isBlank()) {
                out.put("effects.firework.colors",
                    List.of(colors.toUpperCase(Locale.ROOT).split("\\s*,\\s*")));
            }
        }
        // Global usage limit -> max-uses (a CrazyVouchers limiter caps total redemptions).
        if (v.getBoolean("options.limiter.toggle", false)) {
            out.put("max-uses", v.getInt("options.limiter.amount", -1));
        }

        List<Map<String, Object>> conditions = buildConditions(v, warnings, id);
        if (!conditions.isEmpty()) {
            out.put("conditions", conditions);
        }

        reportUnmapped(v, warnings, id);
        return out;
    }

    /**
     * Builds ProVouchers conditions from CrazyVouchers gates: a world whitelist becomes a
     * {@code world} condition, and a permission whitelist becomes a {@code permission}
     * condition per node. A blacklist permission (deny if held) has no equivalent and warns.
     */
    private List<Map<String, Object>> buildConditions(ConfigurationSection v, List<String> warnings, String id) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (v.getBoolean("options.whitelist-worlds.toggle", false)) {
            List<String> worlds = v.getStringList("options.whitelist-worlds.worlds");
            if (!worlds.isEmpty()) {
                conditions.add(condition("world", "worlds", worlds,
                    v.getString("options.whitelist-worlds.message")));
            }
        }
        List<String> whitelist = v.getStringList("options.permission.whitelist-permission.permissions");
        boolean wlOn = v.getBoolean("options.permission.whitelist-permission.toggle", false);
        String wlMessage = v.getString("options.permission.whitelist-permission.message");
        for (String node : wlOn ? whitelist : List.<String>of()) {
            conditions.add(condition("permission", "permission", node, wlMessage));
        }
        // Legacy formats keep a flat permission key.
        String legacy = firstString(v, "options.required-permission", "permission");
        if (legacy != null && !wlOn) {
            conditions.add(condition("permission", "permission", legacy, null));
        }
        if (v.getBoolean("options.permission.blacklist-permission.toggle", false)) {
            warnings.add(id + ": blacklist-permission (deny if held) has no equivalent, not imported");
        }
        return conditions;
    }

    /** A condition map of {@code type}, its keyed value, and an optional converted deny message. */
    private static Map<String, Object> condition(String type, String key, Object value, @Nullable String message) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", type);
        condition.put(key, value);
        if (message != null && !message.isBlank()) {
            // {prefix} is a locale-file token with no meaning in a per-condition message.
            condition.put("deny", LegacyText.toMiniMessage(message.replace("{prefix}", "").trim()));
        }
        return condition;
    }

    /**
     * Parses one entry of the CrazyVouchers give-items DSL
     * ({@code 'Item:x, Name:y, Lore:a,b, Amount:3, Glowing:true'}). Values may contain
     * commas (lore, names), so tokens without a recognised key merge into the previous value.
     */
    private Map<String, Object> convertItemDsl(String dsl, List<String> warnings, String id) {
        Map<String, String> pairs = new LinkedHashMap<>();
        String currentKey = null;
        for (String token : dsl.split(",")) {
            String trimmed = token.trim();
            int colon = trimmed.indexOf(':');
            String maybeKey = colon > 0 ? trimmed.substring(0, colon).toLowerCase(Locale.ROOT) : "";
            if (isDslKey(maybeKey)) {
                currentKey = maybeKey;
                pairs.put(currentKey, trimmed.substring(colon + 1).trim());
            } else if (colon > 0 && !maybeKey.isEmpty() && currentKey == null) {
                warnings.add(id + ": item option '" + trimmed + "' (enchantments are not imported)");
            } else if (currentKey != null) {
                pairs.merge(currentKey, "," + trimmed, String::concat);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("material", pairs.getOrDefault("item", "PAPER").toUpperCase(Locale.ROOT));
        if (pairs.containsKey("name")) {
            out.put("name", LegacyText.toMiniMessage(pairs.get("name")));
        }
        if (pairs.containsKey("lore")) {
            out.put("lore", List.of(pairs.get("lore").split(",")).stream()
                .map(String::trim).map(LegacyText::toMiniMessage).toList());
        }
        if (Boolean.parseBoolean(pairs.getOrDefault("glowing", "false"))) {
            out.put("glow", true);
        }
        if (pairs.containsKey("player")) {
            out.put("skull", Map.of("source", "url", "value", textureUrl(pairs.get("player"))));
        }
        if (pairs.containsKey("skull")) {
            out.put("custom", "hdb:" + pairs.get("skull"));
        }
        if (pairs.containsKey("amount")) {
            out.put("__amount", pairs.get("amount"));
        }
        return out;
    }

    private static boolean isDslKey(String key) {
        return switch (key) {
            case "item", "amount", "name", "lore", "player", "skull", "glowing" -> true;
            default -> false;
        };
    }

    /** Keys this importer understands; anything else surfaces as a per-voucher warning. */
    private static final List<String> HANDLED_PREFIXES = List.of(
        "name", "lore", "item", "glowing", "player", "skull", "custom-model-data",
        "has-argument", "commands", "random-commands", "items", "permission",
        "options.message", "options.sound", "options.firework", "options.required-permission",
        "options.whitelist-worlds", "options.limiter", "options.permission.whitelist-permission",
        // blacklist-permission gets one explicit "no equivalent" warning from buildConditions.
        "options.permission.blacklist-permission",
        // Preview-only visuals and CrazyVouchers-internal toggles with no behavioural equivalent
        // in ProVouchers: skipped without a warning since they would change nothing on import.
        "settings", "components", "override-anti-dupe", "allow-vouchers-in-item-frames",
        "display-damage", "display-trim");

    private void reportUnmapped(ConfigurationSection v, List<String> warnings, String id) {
        for (String key : v.getKeys(true)) {
            if (v.isConfigurationSection(key)) {
                continue;
            }
            boolean handled = false;
            for (String prefix : HANDLED_PREFIXES) {
                if (key.equals(prefix) || key.startsWith(prefix + ".")) {
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                warnings.add(id + ": no equivalent for '" + key + "', not imported");
            }
        }
    }

    /** A minecraft-heads style texture hash, or an already-full URL, as a skull URL. */
    private static String textureUrl(String value) {
        return value.startsWith("http") ? value : "http://textures.minecraft.net/texture/" + value;
    }

    @Nullable
    private static String firstString(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            String value = section.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String sanitizeId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}
