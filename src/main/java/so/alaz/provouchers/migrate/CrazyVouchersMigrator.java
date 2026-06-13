package so.alaz.provouchers.migrate;

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
 * Imports CrazyVouchers configs into ProVouchers files. Vouchers come from the modern
 * per-file layout ({@code CrazyVouchers/vouchers/*.yml}, a root {@code voucher:} section)
 * and the legacy {@code vouchers.yml}; codes from {@code CrazyVouchers/codes/*.yml}
 * ({@code voucher-code:}) and the legacy {@code codes.yml} ({@code voucher-codes:}).
 * Existing ProVouchers ids are never overwritten. Text converts from legacy color codes
 * and curly tokens; every key with no ProVouchers equivalent is reported, never silently
 * dropped, so an admin can audit a migration completely.
 */
public final class CrazyVouchersMigrator implements Migrator {

    private final File sourceDir;
    private final File vouchersDir;
    private final File codesDir;
    private final VoucherRegistry registry;

    public CrazyVouchersMigrator(File dataFolder, VoucherRegistry registry) {
        this.sourceDir = new File(dataFolder.getParentFile(), "CrazyVouchers");
        this.vouchersDir = new File(dataFolder, "vouchers");
        this.codesDir = new File(dataFolder, "codes");
        this.registry = registry;
    }

    @Override
    public String id() {
        return "crazyvouchers";
    }

    @Override
    public String displayName() {
        return "CrazyVouchers";
    }

    @Override
    public boolean isPresent() {
        return sourceDir.isDirectory();
    }

    @Override
    public MigrationReport migrate() {
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        forEachSection(new File(sourceDir, "vouchers"), "voucher",
            new File(sourceDir, "vouchers.yml"), "vouchers",
            (name, section) -> importVoucher(name, section, imported, skipped, warnings));
        forEachSection(new File(sourceDir, "codes"), "voucher-code",
            new File(sourceDir, "codes.yml"), "voucher-codes",
            (name, section) -> importCode(name, section, imported, skipped, warnings));

        return new MigrationReport(imported, skipped, warnings);
    }

    @FunctionalInterface
    private interface SectionConsumer {
        void accept(String name, ConfigurationSection section);
    }

    /**
     * Visits every voucher/code: the per-file entries under {@code perFileDir} (each a
     * {@code rootKey} section) and the named sections under {@code legacyChild} of
     * {@code legacyFile}.
     */
    private void forEachSection(File perFileDir, String rootKey, File legacyFile, String legacyChild,
                                SectionConsumer consumer) {
        File[] files = perFileDir.isDirectory()
            ? perFileDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"))
            : null;
        if (files != null) {
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4);
                ConfigurationSection section =
                    YamlConfiguration.loadConfiguration(file).getConfigurationSection(rootKey);
                if (section != null) {
                    consumer.accept(name, section);
                }
            }
        }
        if (legacyFile.isFile()) {
            ConfigurationSection root =
                YamlConfiguration.loadConfiguration(legacyFile).getConfigurationSection(legacyChild);
            if (root != null) {
                for (String name : root.getKeys(false)) {
                    ConfigurationSection section = root.getConfigurationSection(name);
                    if (section != null) {
                        consumer.accept(name, section);
                    }
                }
            }
        }
    }

    // ---- Vouchers -----------------------------------------------------------

    private void importVoucher(String name, ConfigurationSection voucher,
                               List<String> imported, List<String> skipped, List<String> warnings) {
        String id = sanitizeId(name);
        File target = new File(vouchersDir, id + ".yml");
        if (target.isFile() || registry.getVoucher(id).isPresent()) {
            skipped.add("voucher " + id + ": a ProVouchers voucher with this id already exists");
            return;
        }
        write(target, convertVoucher(voucher, warnings, id), id, imported, skipped, "voucher");
    }

    private Map<String, Object> convertVoucher(ConfigurationSection v, List<String> warnings, String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        convertAppearance(v, out);
        if (v.getBoolean("has-argument", false)) {
            out.put("has-argument", true);
        }
        if (v.getBoolean("options.two-step-authentication", false)) {
            out.put("two-step-authentication", true);
        }

        List<String> rewards = convertRewards(v, warnings, id);
        Map<String, Object> definedItems = convertDefinedItems(v, warnings, id, rewards);
        List<Map<String, Object>> random = convertRandomRewards(v, rewards, warnings, id);
        if (!definedItems.isEmpty()) {
            out.put("items", definedItems);
        }
        if (!rewards.isEmpty()) {
            out.put("rewards", rewards);
        }
        if (!random.isEmpty()) {
            out.put("random-rewards", random);
        }

        if (v.getBoolean("cooldown.toggle", false)) {
            out.put("cooldown", v.getInt("cooldown.interval", 5));
        }
        convertEffects(v, out, warnings, id);
        applyLimiter(v, out);
        List<Map<String, Object>> conditions = buildConditions(v, warnings, id);
        if (!conditions.isEmpty()) {
            out.put("conditions", conditions);
        }

        reportUnmapped(v, warnings, id, VOUCHER_HANDLED);
        return out;
    }

    /** Name, lore, and every item-appearance key, including the alternate {@code settings.} block. */
    private void convertAppearance(ConfigurationSection v, Map<String, Object> out) {
        String name = v.getString("name");
        if (name != null) {
            out.put("display-name", LegacyText.toMiniMessage(name));
        }
        List<String> lore = v.getStringList("lore");
        if (!lore.isEmpty()) {
            out.put("lore", lore.stream().map(LegacyText::toMiniMessage).toList());
        }
        out.put("item.material", v.getString("item", "PAPER").toUpperCase(Locale.ROOT));
        // CrazyVouchers glowing is an enum string (add_glow / remove_glow / none), not a boolean.
        // It can also live under settings.glowing on a head voucher.
        if ("add_glow".equalsIgnoreCase(v.getString("glowing", v.getString("settings.glowing", "none")))) {
            out.put("item.glow", true);
        }
        // -1 is the CrazyVouchers "no model data" sentinel; a real value is positive.
        if (v.getInt("custom-model-data", -1) > 0) {
            out.put("item.custom-model-data", v.getInt("custom-model-data"));
        }
        String texture = firstNonBlank(v, "player", "settings.player");
        if (texture != null) {
            out.put("item.skull.source", "url");
            out.put("item.skull.value", textureUrl(texture));
        }
        String hdb = firstNonBlank(v, "skull", "settings.skull");
        if (hdb != null) {
            out.put("item.custom", "hdb:" + hdb);
        }
        if (v.contains("settings.damage")) {
            out.put("item.damage", v.getInt("settings.damage"));
        }
        String trimMaterial = v.getString("settings.trim.material");
        String trimPattern = v.getString("settings.trim.pattern");
        if (trimMaterial != null && !trimMaterial.isBlank() && trimPattern != null && !trimPattern.isBlank()) {
            out.put("item.trim.material", trimMaterial);
            out.put("item.trim.pattern", trimPattern);
        }
        // The modern item model (1.21.4+), a namespace + key pair.
        String namespace = v.getString("components.item-model.namespace", "");
        String key = v.getString("components.item-model.key", "");
        if (!namespace.isBlank() && !key.isBlank()) {
            out.put("item.item-model", namespace + ":" + key);
        }
        if (v.getBoolean("components.hide-tooltip", false)) {
            out.put("item.hide-tooltip", true);
        }
        // Item dye: a named settings.color, or an r,g,b settings.rgb triple.
        String color = firstNonBlank(v, "settings.color", "settings.rgb");
        if (color != null) {
            out.put("item.color", color);
        }
    }

    // ---- Codes --------------------------------------------------------------

    private void importCode(String name, ConfigurationSection code,
                            List<String> imported, List<String> skipped, List<String> warnings) {
        String id = sanitizeId(name);
        File target = new File(codesDir, id + ".yml");
        if (target.isFile()) {
            skipped.add("code " + id + ": a ProVouchers code file with this id already exists");
            return;
        }
        write(target, convertCode(code, warnings, id), id, imported, skipped, "code");
    }

    private Map<String, Object> convertCode(ConfigurationSection c, List<String> warnings, String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        String code = c.getString("code");
        if (code != null && !code.isBlank()) {
            out.put("code", code);
        }
        if (c.isBoolean("options.case-sensitive")) {
            out.put("case-sensitive", c.getBoolean("options.case-sensitive"));
        }
        if (c.isBoolean("options.enabled") && !c.getBoolean("options.enabled")) {
            out.put("enabled", false);
        }

        List<String> rewards = convertRewards(c, warnings, id);
        List<Map<String, Object>> random = convertRandomRewards(c, rewards, warnings, id);
        if (!rewards.isEmpty()) {
            out.put("rewards", rewards);
        }
        if (!random.isEmpty()) {
            out.put("random-rewards", random);
        }
        applyLimiter(c, out);
        convertEffects(c, out, warnings, id);
        List<Map<String, Object>> conditions = buildConditions(c, warnings, id);
        if (!conditions.isEmpty()) {
            out.put("conditions", conditions);
        }

        reportUnmapped(c, warnings, id, CODE_HANDLED);
        return out;
    }

    // ---- Shared conversion --------------------------------------------------

    /** Guaranteed rewards: {@code commands} as console commands plus the right-click {@code message}. */
    private List<String> convertRewards(ConfigurationSection v, List<String> warnings, String id) {
        List<String> rewards = new ArrayList<>();
        for (String command : v.getStringList("commands")) {
            rewards.add("command: " + LegacyText.toMiniMessage(command));
        }
        String message = v.getString("options.message");
        if (message != null && !message.isBlank()) {
            rewards.add("message: " + LegacyText.toMiniMessage(stripPrefix(message)));
        }
        return rewards;
    }

    /**
     * Weighted reward sets from {@code chance-commands} and {@code random-commands}. CrazyVouchers
     * runs its weighted and unweighted entries as two independent picks: weighted entries become
     * {@code random-rewards}; a single unweighted entry always runs, so it is appended to the
     * guaranteed {@code rewards}; several unweighted entries are a one-of pick, which only collides
     * with the weighted pool (ProVouchers rolls one set per redeem), and that case is reported.
     */
    private List<Map<String, Object>> convertRandomRewards(ConfigurationSection v, List<String> rewards,
                                                           List<String> warnings, String id) {
        List<Map<String, Object>> sets = new ArrayList<>();
        // The legacy list form: each line is "<chance> <command>".
        for (String line : v.getStringList("chance-commands")) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2 && tryInt(parts[0]).isPresent()) {
                sets.add(rewardSet(tryInt(parts[0]).getAsInt(), parts[1]));
            } else if (!line.isBlank()) {
                sets.add(rewardSet(1.0, line));
            }
        }
        ConfigurationSection rc = v.getConfigurationSection("random-commands");
        if (rc == null) {
            return sets;
        }
        List<List<String>> unweighted = new ArrayList<>();
        boolean weighted = false;
        for (String key : rc.getKeys(false)) {
            ConfigurationSection entry = rc.getConfigurationSection(key);
            if (entry == null || entry.getStringList("commands").isEmpty()) {
                continue;
            }
            List<String> commands = entry.getStringList("commands");
            if (entry.isSet("weight")) {
                weighted = true;
                Map<String, Object> set = new LinkedHashMap<>();
                set.put("weight", entry.getDouble("weight"));
                set.put("rewards", commandRewards(commands, true));
                sets.add(set);
            } else {
                unweighted.add(commands);
            }
        }
        if (unweighted.size() == 1) {
            // A single unweighted entry always runs in CrazyVouchers: it is a guaranteed reward.
            rewards.addAll(commandRewards(unweighted.get(0), false));
        } else {
            for (List<String> commands : unweighted) {
                Map<String, Object> set = new LinkedHashMap<>();
                set.put("weight", 1.0);
                set.put("rewards", commandRewards(commands, false));
                sets.add(set);
            }
            if (!unweighted.isEmpty() && weighted) {
                warnings.add(id + ": random-commands has a weighted pool and an unweighted one; "
                    + "ProVouchers rolls a single set per redeem, so they were merged");
            }
        }
        return sets;
    }

    private static List<String> commandRewards(List<String> commands, boolean stripWeight) {
        List<String> rewards = new ArrayList<>(commands.size());
        for (String command : commands) {
            rewards.add("command: " + LegacyText.toMiniMessage(stripLegacyWeight(command, stripWeight)));
        }
        return rewards;
    }

    private void convertEffects(ConfigurationSection v, Map<String, Object> out, List<String> warnings, String id) {
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
            // "firework" is the current key; some configs carry the "fireworks" typo.
            String colors = v.getString("options.firework.colors", v.getString("options.fireworks.colors", ""));
            if (!colors.isBlank()) {
                out.put("effects.firework.colors",
                    List.of(colors.toUpperCase(Locale.ROOT).split("\\s*,\\s*")));
            }
        }
    }

    /** A CrazyVouchers limiter caps total redemptions, which maps to {@code max-uses}. */
    private void applyLimiter(ConfigurationSection v, Map<String, Object> out) {
        if (v.getBoolean("options.limiter.toggle", false)) {
            // "limit" is the v4 key; older configs used "amount".
            out.put("max-uses", v.getInt("options.limiter.limit", v.getInt("options.limiter.amount", -1)));
        }
    }

    /**
     * World and permission whitelists become {@code world} and {@code permission} conditions.
     * A blacklist permission (deny if held) has no equivalent and is reported.
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
        boolean wlOn = v.getBoolean("options.permission.whitelist-permission.toggle", false);
        if (wlOn) {
            String message = v.getString("options.permission.whitelist-permission.message");
            for (String node : v.getStringList("options.permission.whitelist-permission.permissions")) {
                conditions.add(condition("permission", "permission", node, message));
            }
            // Some configs use a single "node" instead of the permissions list.
            String single = v.getString("options.permission.whitelist-permission.node");
            if (single != null && !single.isBlank()) {
                conditions.add(condition("permission", "permission", single, message));
            }
        }
        if (v.getBoolean("options.permission.blacklist-permission.toggle", false)) {
            warnings.add(id + ": blacklist-permission (deny if held) has no equivalent, not imported");
        }
        // Required placeholders become equality papi conditions.
        ConfigurationSection required = v.getConfigurationSection("options.required-placeholders");
        if (required != null) {
            String message = v.getString("options.required-placeholders-message");
            for (String key : required.getKeys(false)) {
                ConfigurationSection entry = required.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                String placeholder = entry.getString("placeholder", "");
                String value = entry.getString("value", "");
                if (placeholder.isBlank() || value.isBlank()) {
                    continue;
                }
                Map<String, Object> condition = condition("papi", "placeholder", placeholder, message);
                condition.put("operator", "equals");
                condition.put("value", value);
                conditions.add(condition);
            }
        }
        return conditions;
    }

    private static Map<String, Object> condition(String type, String key, Object value, @Nullable String message) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", type);
        condition.put(key, value);
        if (message != null && !message.isBlank()) {
            condition.put("deny", LegacyText.toMiniMessage(stripPrefix(message)));
        }
        return condition;
    }

    // ---- Item-give DSL ------------------------------------------------------

    private Map<String, Object> convertDefinedItems(ConfigurationSection v, List<String> warnings, String id,
                                                    List<String> rewards) {
        Map<String, Object> definedItems = new LinkedHashMap<>();
        int index = 0;
        for (String dsl : v.getStringList("items")) {
            index++;
            String key = "imported_" + index;
            Map<String, Object> defined = convertItemDsl(dsl, warnings, id);
            Object amount = defined.remove("__amount");
            definedItems.put(key, defined);
            rewards.add("item: @" + key + (amount != null ? " " + amount : ""));
        }
        return definedItems;
    }

    /**
     * Parses one entry of the give-items DSL
     * ({@code 'Item:x, Name:y, Lore:a,b, Amount:3, Damage:50, Trim:pattern!material, sharpness:5'}).
     * Recognised keys map directly; {@code damage} and {@code trim} map to the item appearance;
     * any other {@code name:level} token is treated as an enchantment. Values may contain commas
     * (lore, names), so a token with no key merges into the previous value.
     */
    private Map<String, Object> convertItemDsl(String dsl, List<String> warnings, String id) {
        Map<String, String> pairs = new LinkedHashMap<>();
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        String currentKey = null;
        for (String token : dsl.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String maybeKey = colon > 0 ? trimmed.substring(0, colon).toLowerCase(Locale.ROOT) : "";
            String value = colon > 0 ? trimmed.substring(colon + 1).trim() : "";
            if (isDslKey(maybeKey)) {
                currentKey = maybeKey;
                pairs.put(currentKey, value);
            } else if (colon > 0 && !maybeKey.isEmpty()) {
                // An unrecognised key:value: an enchantment when the value is a level number.
                currentKey = null;
                try {
                    enchantments.put(maybeKey, Integer.parseInt(value));
                } catch (NumberFormatException ex) {
                    warnings.add(id + ": item option '" + trimmed + "' has no equivalent, not imported");
                }
            } else if (currentKey != null) {
                pairs.merge(currentKey, ", " + trimmed, String::concat);
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
        if (pairs.containsKey("damage")) {
            tryInt(pairs.get("damage")).ifPresent(damage -> out.put("damage", damage));
        }
        // Trim is written pattern!material in the DSL.
        if (pairs.containsKey("trim")) {
            String[] parts = pairs.get("trim").split("!", 2);
            if (parts.length == 2) {
                out.put("trim", Map.of("pattern", parts[0].trim(), "material", parts[1].trim()));
            }
        }
        if (!enchantments.isEmpty()) {
            out.put("enchantments", enchantments);
        }
        if (pairs.containsKey("amount")) {
            out.put("__amount", pairs.get("amount"));
        }
        return out;
    }

    private static boolean isDslKey(String key) {
        return switch (key) {
            case "item", "amount", "name", "lore", "player", "skull", "glowing", "damage", "trim" -> true;
            default -> false;
        };
    }

    private static java.util.OptionalInt tryInt(String value) {
        try {
            return java.util.OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return java.util.OptionalInt.empty();
        }
    }

    /** A single-command weighted reward set. */
    private static Map<String, Object> rewardSet(double weight, String command) {
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("weight", weight);
        set.put("rewards", List.of("command: " + LegacyText.toMiniMessage(command.trim())));
        return set;
    }

    /** The first of {@code paths} with a non-blank value, or {@code null}. */
    @Nullable
    private static String firstNonBlank(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            String value = section.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // ---- Reporting ----------------------------------------------------------

    /** Voucher keys this importer converts; every other leaf key is reported as unmapped. */
    private static final List<String> VOUCHER_HANDLED = List.of(
        "name", "lore", "item", "glowing", "custom-model-data", "player", "skull",
        "has-argument", "commands", "random-commands", "chance-commands", "items", "cooldown",
        "settings.glowing", "settings.player", "settings.skull", "settings.damage", "settings.trim",
        "settings.color", "settings.rgb",
        "components.item-model", "components.hide-tooltip",
        "options.message", "options.sound", "options.firework", "options.fireworks", "options.limiter",
        "options.two-step-authentication", "options.whitelist-worlds",
        "options.required-placeholders", "options.required-placeholders-message",
        "options.permission.whitelist-permission", "options.permission.blacklist-permission");

    /** Code keys this importer converts. */
    private static final List<String> CODE_HANDLED = List.of(
        "code", "commands", "random-commands", "chance-commands",
        "options.message", "options.case-sensitive", "options.enabled", "options.limiter", "options.sound",
        "options.firework", "options.fireworks", "options.whitelist-worlds",
        "options.required-placeholders", "options.required-placeholders-message",
        "options.permission.whitelist-permission", "options.permission.blacklist-permission");

    private void reportUnmapped(ConfigurationSection v, List<String> warnings, String id, List<String> handled) {
        for (String key : v.getKeys(true)) {
            if (v.isConfigurationSection(key)) {
                continue;
            }
            boolean isHandled = false;
            for (String prefix : handled) {
                if (key.equals(prefix) || key.startsWith(prefix + ".")) {
                    isHandled = true;
                    break;
                }
            }
            if (!isHandled) {
                warnings.add(id + ": no equivalent for '" + key + "', not imported");
            }
        }
    }

    // ---- Helpers ------------------------------------------------------------

    private void write(File target, Map<String, Object> data, String id,
                       List<String> imported, List<String> skipped, String kind) {
        YamlConfiguration out = new YamlConfiguration();
        out.options().setHeader(List.of(
            "Imported from CrazyVouchers by /voucher import.",
            "Review the result, then apply edits with /voucher reload."));
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            out.set(entry.getKey(), entry.getValue());
        }
        try {
            target.getParentFile().mkdirs();
            out.save(target);
            imported.add(kind + " " + id);
        } catch (IOException ex) {
            skipped.add(kind + " " + id + ": could not write file (" + ex.getMessage() + ")");
        }
    }

    /** Drops a leading legacy inline weight ({@code "45 eco ..."}) when an explicit weight exists. */
    private static String stripLegacyWeight(String command, boolean hasWeightField) {
        return hasWeightField ? command.replaceFirst("^\\d+(\\.\\d+)?\\s+", "") : command;
    }

    /** {@code {prefix}} is a locale-file token with no meaning in ProVouchers message bodies. */
    private static String stripPrefix(String value) {
        return value.replace("{prefix}", "").trim();
    }

    private static String textureUrl(String value) {
        return value.startsWith("http") ? value : "http://textures.minecraft.net/texture/" + value;
    }

    private static String sanitizeId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}
