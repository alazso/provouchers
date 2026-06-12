package so.alaz.provouchers.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardLineParser;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.util.Expiry;
import so.alaz.provouchers.reward.RewardItemPayload;
import so.alaz.provouchers.reward.RewardType;
import so.alaz.provouchers.voucher.CustomItemRef;
import so.alaz.provouchers.voucher.DefinedItem;
import so.alaz.provouchers.voucher.FireworkSpec;
import so.alaz.provouchers.voucher.Materials;
import so.alaz.provouchers.voucher.SkullSpec;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherEffects;
import so.alaz.provouchers.voucher.VoucherItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a parsed YAML section into a {@link Voucher} or {@link VoucherCode}, with
 * eager validation. Every failure raises a {@link VoucherParseException} naming
 * the key at fault, so a malformed file is reported rather than silently skipped.
 */
public final class VoucherParser {

    private VoucherParser() {
    }

    /** Parses a voucher whose id defaults to {@code id} when no {@code id} key is present. */
    public static Voucher parseVoucher(ConfigurationSection section, String id) {
        String voucherId = section.getString("id", id);
        if (voucherId == null || voucherId.isBlank()) {
            throw new VoucherParseException("voucher '" + id + "': id must not be blank");
        }
        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection == null) {
            throw new VoucherParseException("voucher '" + voucherId + "': missing required 'item' section");
        }
        VoucherItem item = parseItem(itemSection, voucherId);

        long cooldown = section.getLong("cooldown", 0L);
        boolean stackable = section.getBoolean("stackable", true);
        boolean batchOpen = section.getBoolean("batch-open", false);
        if (batchOpen && cooldown > 0) {
            throw new VoucherParseException(
                "voucher '" + voucherId + "': batch-open cannot be combined with a cooldown");
        }
        if (batchOpen && !stackable) {
            throw new VoucherParseException(
                "voucher '" + voucherId + "': batch-open requires a stackable voucher");
        }

        Map<String, DefinedItem> definedItems = parseDefinedItems(section, voucherId);
        List<RewardLine> rewards = parseRewards(section.getStringList("rewards"), voucherId);
        List<RewardSet> randomRewards = parseRandomRewards(section, voucherId);
        validateItemRefs(rewards, randomRewards, definedItems, voucherId);

        return new Voucher(
            voucherId,
            section.getString("display-name"),
            section.getStringList("lore"),
            item,
            parseConditions(section, voucherId),
            rewards,
            randomRewards,
            definedItems,
            section.getBoolean("unredeemable", false),
            section.getBoolean("owner-only", false),
            cooldown,
            parseLimit(section, "max-uses", voucherId),
            parseLimit(section, "uses-per-player", voucherId),
            parseExpiry(section, voucherId),
            parseActiveFrom(section, voucherId),
            section.getBoolean("has-argument", false),
            stackable,
            batchOpen,
            section.getBoolean("two-step-authentication", false),
            emptyToNull(section.getString("two-step-authentication-message", "")),
            parseEffects(section, voucherId)
        );
    }

    /** Parses the optional {@code effects} block (sound, firework), or {@code null} when absent or empty. */
    @Nullable
    private static VoucherEffects parseEffects(ConfigurationSection section, String id) {
        ConfigurationSection effects = section.getConfigurationSection("effects");
        if (effects == null) {
            return null;
        }
        String sound = emptyToNull(effects.getString("sound", ""));
        FireworkSpec firework = null;
        ConfigurationSection fireworkSection = effects.getConfigurationSection("firework");
        if (fireworkSection != null) {
            try {
                firework = FireworkSpec.of(
                    fireworkSection.getStringList("colors"),
                    fireworkSection.getStringList("fade"),
                    fireworkSection.getString("type", "BALL"),
                    fireworkSection.getInt("power", 0));
            } catch (IllegalArgumentException ex) {
                throw new VoucherParseException("voucher '" + id + "': " + ex.getMessage(), ex);
            }
        }
        return sound == null && firework == null ? null : new VoucherEffects(sound, firework);
    }

    /** Parses a code whose code value defaults to {@code id} when no {@code code} key is present. */
    public static VoucherCode parseCode(ConfigurationSection section, String id) {
        String code = section.getString("code", id);
        if (code == null || code.isBlank()) {
            throw new VoucherParseException("code '" + id + "': code must not be blank");
        }
        int usesPerPlayer = section.getInt("uses-per-player", 1);
        if (usesPerPlayer < 1) {
            throw new VoucherParseException("code '" + code + "': uses-per-player must be at least 1");
        }
        Map<String, DefinedItem> definedItems = parseDefinedItems(section, code);
        List<RewardLine> rewards = parseRewards(section.getStringList("rewards"), code);
        List<RewardSet> randomRewards = parseRandomRewards(section, code);
        validateItemRefs(rewards, randomRewards, definedItems, code);
        return new VoucherCode(
            code,
            section.getBoolean("case-sensitive", false),
            section.getInt("max-uses", -1),
            usesPerPlayer,
            parseExpiry(section, code),
            parseActiveFrom(section, code),
            parseConditions(section, code),
            rewards,
            randomRewards,
            definedItems,
            section.getBoolean("has-argument", false)
        );
    }

    /** The {@code items:} map of reusable decorated items, keyed by lower-cased name. */
    private static Map<String, DefinedItem> parseDefinedItems(ConfigurationSection section, String id) {
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items == null) {
            return Map.of();
        }
        Map<String, DefinedItem> result = new LinkedHashMap<>();
        for (String name : items.getKeys(false)) {
            ConfigurationSection entry = items.getConfigurationSection(name);
            if (entry == null) {
                throw new VoucherParseException("'" + id + "': items." + name + " must be a section");
            }
            result.put(name.toLowerCase(Locale.ROOT), new DefinedItem(
                entry.getString("name"),
                entry.getStringList("lore"),
                parseItem(entry, id + "' items '" + name)));
        }
        return result;
    }

    /** Rejects an {@code item: @name} reward whose name is not in the {@code items:} map. */
    private static void validateItemRefs(List<RewardLine> rewards, List<RewardSet> randomRewards,
                                         Map<String, DefinedItem> definedItems, String id) {
        List<RewardLine> all = new ArrayList<>(rewards);
        for (RewardSet set : randomRewards) {
            all.addAll(set.rewards());
        }
        for (RewardLine line : all) {
            if (line.type() != RewardType.ITEM) {
                continue;
            }
            String reference = RewardItemPayload.parse(line.payload()).reference();
            if (reference.startsWith("@")
                && !definedItems.containsKey(reference.substring(1).toLowerCase(Locale.ROOT))) {
                throw new VoucherParseException("'" + id + "': reward '" + line.payload()
                    + "' references undefined item '" + reference.substring(1) + "'");
            }
        }
    }

    /** A use limit: {@code -1} (unlimited, the default) or a positive count. */
    private static int parseLimit(ConfigurationSection section, String key, String id) {
        int value = section.getInt(key, -1);
        if (value == 0 || value < -1) {
            throw new VoucherParseException("voucher '" + id + "': " + key + " must be -1 or at least 1");
        }
        return value;
    }

    /** Validates the optional absolute {@code active-from} gate; a relative duration is rejected. */
    @Nullable
    private static String parseActiveFrom(ConfigurationSection section, String id) {
        String raw = emptyToNull(section.getString("active-from", ""));
        if (raw == null) {
            return null;
        }
        if (Expiry.isRelative(raw)) {
            throw new VoucherParseException("'" + id + "': active-from '" + raw
                + "' must be an absolute date or instant, not a relative duration");
        }
        try {
            Expiry.resolveStart(raw);
        } catch (IllegalArgumentException ex) {
            throw new VoucherParseException("'" + id + "': " + ex.getMessage(), ex);
        }
        return raw;
    }

    private static VoucherItem parseItem(ConfigurationSection item, String id) {
        String custom = emptyToNull(item.getString("custom", ""));
        if (custom != null && !ItemBuilder.isSerialized(custom)) {
            CustomItemRef ref;
            try {
                ref = CustomItemRef.parse(custom);
            } catch (IllegalArgumentException ex) {
                throw new VoucherParseException("voucher '" + id + "': item.custom " + ex.getMessage(), ex);
            }
            if (!ref.hasKnownProvider()) {
                throw new VoucherParseException("voucher '" + id + "': item.custom names unknown provider '"
                    + ref.providerHint() + "'");
            }
        }
        String material = item.getString("material", "PAPER");
        if (material == null || material.isBlank()) {
            if (custom == null) {
                throw new VoucherParseException("voucher '" + id + "': item.material must not be blank");
            }
            material = "PAPER";
        }
        try {
            Materials.resolve(material);
        } catch (IllegalArgumentException ex) {
            throw new VoucherParseException("voucher '" + id + "': item.material " + ex.getMessage(), ex);
        }
        Integer cmd = item.contains("custom-model-data") ? item.getInt("custom-model-data") : null;
        SkullSpec skull = parseSkull(item.getConfigurationSection("skull"), id);
        return new VoucherItem(material, custom, cmd, item.getBoolean("glow", false), skull);
    }

    @Nullable
    private static SkullSpec parseSkull(@Nullable ConfigurationSection skull, String id) {
        if (skull == null) {
            return null;
        }
        String sourceRaw = skull.getString("source");
        if (sourceRaw == null || sourceRaw.isBlank()) {
            throw new VoucherParseException("voucher '" + id + "': item.skull.source is required");
        }
        try {
            return new SkullSpec(SkullSpec.source(sourceRaw), skull.getString("value", ""));
        } catch (IllegalArgumentException ex) {
            throw new VoucherParseException("voucher '" + id + "': item.skull " + ex.getMessage(), ex);
        }
    }

    @Nullable
    private static String parseExpiry(ConfigurationSection section, String id) {
        String raw = emptyToNull(section.getString("expiry", ""));
        if (raw != null) {
            try {
                Expiry.resolve(raw, Instant.now());
            } catch (IllegalArgumentException ex) {
                throw new VoucherParseException("voucher '" + id + "': expiry " + ex.getMessage(), ex);
            }
        }
        return raw;
    }

    private static List<RewardLine> parseRewards(List<String> lines, String id) {
        List<RewardLine> rewards = new ArrayList<>(lines.size());
        for (String line : lines) {
            try {
                rewards.add(RewardLineParser.parse(line));
            } catch (IllegalArgumentException ex) {
                throw new VoucherParseException("voucher '" + id + "': " + ex.getMessage(), ex);
            }
        }
        return rewards;
    }

    private static List<RewardSet> parseRandomRewards(ConfigurationSection section, String id) {
        List<Map<?, ?>> raw = section.getMapList("random-rewards");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<RewardSet> sets = new ArrayList<>(raw.size());
        int index = 0;
        for (Map<?, ?> entry : raw) {
            index++;
            Object weightValue = entry.get("weight");
            double weight = weightValue instanceof Number number ? number.doubleValue() : 1.0;
            if (weight <= 0) {
                throw new VoucherParseException("voucher '" + id + "': random-rewards[" + index
                    + "].weight must be positive");
            }
            Object rewardsValue = entry.get("rewards");
            if (!(rewardsValue instanceof List<?> rewardList) || rewardList.isEmpty()) {
                throw new VoucherParseException("voucher '" + id + "': random-rewards[" + index
                    + "].rewards must be a non-empty list");
            }
            List<RewardLine> lines = new ArrayList<>(rewardList.size());
            for (Object rewardLine : rewardList) {
                try {
                    lines.add(RewardLineParser.parse(String.valueOf(rewardLine)));
                } catch (IllegalArgumentException ex) {
                    throw new VoucherParseException("voucher '" + id + "': random-rewards[" + index
                        + "]: " + ex.getMessage(), ex);
                }
            }
            sets.add(new RewardSet(weight, lines));
        }
        return sets;
    }

    private static List<Map<String, Object>> parseConditions(ConfigurationSection section, String id) {
        List<Map<?, ?>> raw = section.getMapList("conditions");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> conditions = new ArrayList<>(raw.size());
        int index = 0;
        for (Map<?, ?> entry : raw) {
            index++;
            Map<String, Object> condition = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : entry.entrySet()) {
                condition.put(String.valueOf(field.getKey()), field.getValue());
            }
            Object type = condition.get("type");
            if (!(type instanceof String typeName) || typeName.isBlank()) {
                throw new VoucherParseException("voucher '" + id + "': conditions[" + index
                    + "] is missing a 'type'");
            }
            conditions.add(condition);
        }
        return conditions;
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
