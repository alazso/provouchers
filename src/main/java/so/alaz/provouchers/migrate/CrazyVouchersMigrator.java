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
        if (!definedItems.isEmpty()) {
            out.put("items", definedItems);
        }
        if (!rewards.isEmpty()) {
            out.put("rewards", rewards);
        }
        List<Map<String, Object>> random = convertRandomRewards(v, warnings, id);
        if (!random.isEmpty()) {
            out.put("random-rewards", random);
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

    /** Name, lore, material, glow, custom model data, and a player-head texture. */
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
        if ("add_glow".equalsIgnoreCase(v.getString("glowing", "none"))) {
            out.put("item.glow", true);
        }
        // -1 is the CrazyVouchers "no model data" sentinel; a real value is positive.
        if (v.getInt("custom-model-data", -1) > 0) {
            out.put("item.custom-model-data", v.getInt("custom-model-data"));
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

        List<String> rewards = convertRewards(c, warnings, id);
        if (!rewards.isEmpty()) {
            out.put("rewards", rewards);
        }
        List<Map<String, Object>> random = convertRandomRewards(c, warnings, id);
        if (!random.isEmpty()) {
            out.put("random-rewards", random);
        }
        applyLimiter(c, out);
        List<Map<String, Object>> conditions = buildConditions(c, warnings, id);
        if (!conditions.isEmpty()) {
            out.put("conditions", conditions);
        }
        // Codes have no item, so effects (sound/firework) and any appearance do not apply.
        if (c.getBoolean("options.sound.toggle", false) || c.getBoolean("options.firework.toggle", false)) {
            warnings.add("code " + id + ": sound/firework effects are voucher-only, not imported");
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
     * The {@code random-commands} map. Each entry becomes a weighted reward set; an entry
     * without a weight defaults to weight 1. A stray legacy inline weight prefix on a
     * command (such as {@code "45 eco give ..."}) is dropped when an explicit weight exists.
     */
    private List<Map<String, Object>> convertRandomRewards(ConfigurationSection v, List<String> warnings,
                                                           String id) {
        ConfigurationSection rc = v.getConfigurationSection("random-commands");
        if (rc == null) {
            return List.of();
        }
        List<Map<String, Object>> sets = new ArrayList<>();
        boolean weighted = false;
        boolean unweighted = false;
        for (String key : rc.getKeys(false)) {
            ConfigurationSection entry = rc.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            List<String> commands = entry.getStringList("commands");
            if (commands.isEmpty()) {
                continue;
            }
            boolean hasWeight = entry.isSet("weight");
            weighted |= hasWeight;
            unweighted |= !hasWeight;
            List<String> rewards = new ArrayList<>();
            for (String command : commands) {
                rewards.add("command: " + LegacyText.toMiniMessage(stripLegacyWeight(command, hasWeight)));
            }
            Map<String, Object> set = new LinkedHashMap<>();
            set.put("weight", hasWeight ? entry.getDouble("weight") : 1.0);
            set.put("rewards", rewards);
            sets.add(set);
        }
        if (weighted && unweighted) {
            warnings.add(id + ": random-commands mixed weighted and unweighted entries; merged into one "
                + "weighted pool (ProVouchers rolls one set per redeem)");
        }
        return sets;
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
            String colors = v.getString("options.firework.colors", "");
            if (!colors.isBlank()) {
                out.put("effects.firework.colors",
                    List.of(colors.toUpperCase(Locale.ROOT).split("\\s*,\\s*")));
            }
        }
    }

    /** A CrazyVouchers limiter caps total redemptions, which maps to {@code max-uses}. */
    private void applyLimiter(ConfigurationSection v, Map<String, Object> out) {
        if (v.getBoolean("options.limiter.toggle", false)) {
            out.put("max-uses", v.getInt("options.limiter.amount", -1));
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
        }
        if (v.getBoolean("options.permission.blacklist-permission.toggle", false)) {
            warnings.add(id + ": blacklist-permission (deny if held) has no equivalent, not imported");
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
     * Parses one entry of the give-items DSL ({@code 'Item:x, Name:y, Lore:a,b, Amount:3'}).
     * Values may contain commas (lore, names), so a token with no recognised key merges into
     * the previous value. Damage, trim, and enchantment tokens have no equivalent on a reward
     * item and are reported rather than applied.
     */
    private Map<String, Object> convertItemDsl(String dsl, List<String> warnings, String id) {
        Map<String, String> pairs = new LinkedHashMap<>();
        String currentKey = null;
        for (String token : dsl.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String maybeKey = colon > 0 ? trimmed.substring(0, colon).toLowerCase(Locale.ROOT) : "";
            if (isDslKey(maybeKey)) {
                currentKey = maybeKey;
                pairs.put(currentKey, trimmed.substring(colon + 1).trim());
            } else if (colon > 0 && !maybeKey.isEmpty()) {
                // damage, trim, or an enchantment (name:level): no reward-item equivalent.
                warnings.add(id + ": item option '" + trimmed + "' has no equivalent, not imported");
                currentKey = null;
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

    // ---- Reporting ----------------------------------------------------------

    /** Voucher keys this importer converts; every other leaf key is reported as unmapped. */
    private static final List<String> VOUCHER_HANDLED = List.of(
        "name", "lore", "item", "glowing", "custom-model-data", "player", "skull",
        "has-argument", "commands", "random-commands", "items",
        "options.message", "options.sound", "options.firework", "options.limiter",
        "options.two-step-authentication", "options.whitelist-worlds",
        "options.permission.whitelist-permission", "options.permission.blacklist-permission");

    /** Code keys this importer converts. */
    private static final List<String> CODE_HANDLED = List.of(
        "code", "commands", "random-commands",
        "options.message", "options.case-sensitive", "options.limiter", "options.sound",
        "options.firework", "options.whitelist-worlds",
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
