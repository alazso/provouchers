package so.alaz.provouchers.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardLineParser;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.util.Expiry;
import so.alaz.provouchers.voucher.CustomItemRef;
import so.alaz.provouchers.voucher.Materials;
import so.alaz.provouchers.voucher.SkullSpec;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

        return new Voucher(
            voucherId,
            section.getString("display-name"),
            section.getStringList("lore"),
            item,
            parseConditions(section, voucherId),
            parseRewards(section.getStringList("rewards"), voucherId),
            parseRandomRewards(section, voucherId),
            section.getBoolean("unredeemable", false),
            section.getBoolean("owner-only", false),
            cooldown,
            parseExpiry(section, voucherId),
            section.getBoolean("has-argument", false),
            stackable,
            batchOpen
        );
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
        return new VoucherCode(
            code,
            section.getBoolean("case-sensitive", false),
            section.getInt("max-uses", -1),
            usesPerPlayer,
            parseExpiry(section, code),
            parseConditions(section, code),
            parseRewards(section.getStringList("rewards"), code),
            parseRandomRewards(section, code),
            section.getBoolean("has-argument", false)
        );
    }

    private static VoucherItem parseItem(ConfigurationSection item, String id) {
        String custom = emptyToNull(item.getString("custom", ""));
        if (custom != null) {
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
