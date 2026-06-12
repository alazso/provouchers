package so.alaz.provouchers.condition;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.util.Durations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The hook-free built-in conditions and their registration. Each renders its {@code deny} message
 * (or a per-condition default) through {@link Text} so failures support MiniMessage and placeholders.
 */
final class BuiltinConditions {

    private BuiltinConditions() {
    }

    static void registerAll(ConditionRegistry registry, Text text) {
        registry.register("permission", section ->
            new Permission(orEmpty(section.getString("permission")), section.getString("deny"), text));
        registry.register("world", section ->
            new World(new HashSet<>(section.getStringList("worlds")), section.getString("deny"), text));
        registry.register("gamemode", section ->
            new Gamemode(upperSet(section.getStringList("gamemodes")), section.getString("deny"), text));
        registry.register("exp", section ->
            new Exp(section.getInt("level"), section.getString("deny"), text));
        registry.register("expiry", section ->
            new Expiry(parseExpiry(section), section.getString("deny"), text));
        registry.register("papi", section ->
            new Papi(orEmpty(section.getString("placeholder")), orDefault(section.getString("operator"), "equals"),
                orEmpty(section.getString("value")), section.getString("deny"), text));
        registry.register("playerstat", section ->
            new Playerstat(orEmpty(section.getString("statistic")), section.getString("material"),
                section.getString("entity"), orDefault(section.getString("operator"), ">="),
                section.getLong("value"), section.getString("deny"), text));
        registry.register("playtime", section ->
            new Playtime(orEmpty(section.getString("time")), section.getString("deny"), text));
        registry.register("item", section ->
            new Item(orEmpty(section.getString("material")), section.getInt("amount", 1),
                section.getString("deny"), text));
        registry.register("advancement", section ->
            new Advancement(orEmpty(section.getString("advancement")), section.getString("deny"), text));
    }

    /** Shared base: renders the configured deny message, or a per-condition default, against the player. */
    abstract static class Base implements Condition {

        @Nullable private final String deny;
        protected final Text text;

        Base(@Nullable String deny, Text text) {
            this.deny = deny;
            this.text = text;
        }

        protected ConditionResult pass() {
            return ConditionResult.pass();
        }

        protected ConditionResult denied(ConditionContext context, String key, Object... placeholders) {
            String template = deny != null
                ? Messages.fill(deny, placeholders)
                : context.messages().get(context.player(), key, placeholders);
            return ConditionResult.fail(text.render(template, context.player()));
        }
    }

    /** Passes if the player has the permission node (via Bukkit, which reflects LuckPerms). */
    static final class Permission extends Base {
        private final String node;

        Permission(String node, @Nullable String deny, Text text) {
            super(deny, text);
            this.node = node;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            return context.player().hasPermission(node) ? pass() : denied(context, "condition.permission");
        }
    }

    /** Passes if the player is in one of the named worlds. */
    static final class World extends Base {
        private final Set<String> worlds;

        World(Set<String> worlds, @Nullable String deny, Text text) {
            super(deny, text);
            this.worlds = worlds;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            return worlds.contains(context.player().getWorld().getName())
                ? pass() : denied(context, "condition.world");
        }
    }

    /** Passes if the player's game mode is one of the named modes. */
    static final class Gamemode extends Base {
        private final Set<String> modes;

        Gamemode(Set<String> modes, @Nullable String deny, Text text) {
            super(deny, text);
            this.modes = modes;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            return modes.contains(context.player().getGameMode().name())
                ? pass() : denied(context, "condition.gamemode");
        }
    }

    /** Passes if the player's level is at least the minimum. */
    static final class Exp extends Base {
        private final int minLevel;

        Exp(int minLevel, @Nullable String deny, Text text) {
            super(deny, text);
            this.minLevel = minLevel;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            return context.player().getLevel() >= minLevel
                ? pass() : denied(context, "condition.exp", "level", minLevel);
        }
    }

    /** Passes only until the expiry instant (epoch millis). */
    static final class Expiry extends Base {
        private final long expiresAtMillis;

        Expiry(long expiresAtMillis, @Nullable String deny, Text text) {
            super(deny, text);
            this.expiresAtMillis = expiresAtMillis;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            return System.currentTimeMillis() <= expiresAtMillis
                ? pass() : denied(context, "condition.expiry");
        }
    }

    /**
     * Compares a resolved PlaceholderAPI placeholder against a configured value. Degrades safely:
     * with PAPI absent the placeholder resolves to its raw text, which simply will not match.
     */
    static final class Papi extends Base {
        private final String placeholder;
        private final String operator;
        private final String value;

        Papi(String placeholder, String operator, String value, @Nullable String deny, Text text) {
            super(deny, text);
            this.placeholder = placeholder;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            String resolved = text.resolve(placeholder, context.player());
            boolean matched = switch (operator.trim().toLowerCase(Locale.ROOT)) {
                case "contains" -> resolved.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
                case "!=", "ne", "not" -> !resolved.equalsIgnoreCase(value);
                case ">", ">=", "<", "<=", "gt", "gte", "lt", "lte" -> {
                    Double left = parseDouble(resolved);
                    Double right = parseDouble(value);
                    yield left != null && right != null && numericCompare(left, operator, right);
                }
                default -> resolved.equalsIgnoreCase(value);
            };
            return matched ? pass() : denied(context, "condition.requirement-not-met");
        }
    }

    /**
     * Compares a player statistic against a threshold. Handles untyped statistics as well as
     * block/item (needs {@code material}) and entity (needs {@code entity}) statistics. Invalid names
     * or a missing sub-type deny gracefully.
     */
    static final class Playerstat extends Base {
        private final String statName;
        @Nullable private final String materialName;
        @Nullable private final String entityName;
        private final String operator;
        private final long threshold;

        Playerstat(String statName, @Nullable String materialName, @Nullable String entityName,
                   String operator, long threshold, @Nullable String deny, Text text) {
            super(deny, text);
            this.statName = statName;
            this.materialName = materialName;
            this.entityName = entityName;
            this.operator = operator;
            this.threshold = threshold;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            Integer value = readStatistic(context.player());
            if (value == null) {
                return denied(context, "condition.statistic-unreadable");
            }
            return numericCompare(value, operator, threshold)
                ? pass() : denied(context, "condition.requirement-not-met");
        }

        @Nullable
        private Integer readStatistic(Player player) {
            try {
                Statistic statistic = Statistic.valueOf(statName.trim().toUpperCase(Locale.ROOT));
                return switch (statistic.getType()) {
                    case UNTYPED -> player.getStatistic(statistic);
                    case ITEM, BLOCK -> player.getStatistic(statistic,
                        Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT)));
                    case ENTITY -> player.getStatistic(statistic,
                        EntityType.valueOf(entityName.trim().toUpperCase(Locale.ROOT)));
                };
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    /**
     * Passes once the player's total playtime reaches the configured duration ({@code 2h}, {@code 30m}).
     * Playtime is read from {@link Statistic#PLAY_ONE_MINUTE}, which despite its name counts ticks.
     */
    static final class Playtime extends Base {
        private final String rawTime;
        @Nullable private final Duration required;

        Playtime(String rawTime, @Nullable String deny, Text text) {
            super(deny, text);
            this.rawTime = rawTime;
            this.required = Durations.parseOrNull(rawTime.trim());
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            if (required == null) {
                return denied(context, "condition.requirement-not-met");
            }
            long playedSeconds = context.player().getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
            return playedSeconds >= required.toSeconds()
                ? pass() : denied(context, "condition.playtime", "time", rawTime);
        }
    }

    /** Passes if the inventory holds at least {@code amount} of the material. Checks only, never consumes. */
    static final class Item extends Base {
        private final String materialName;
        private final int amount;

        Item(String materialName, int amount, @Nullable String deny, Text text) {
            super(deny, text);
            this.materialName = materialName;
            this.amount = Math.max(1, amount);
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            Material material;
            try {
                material = Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return denied(context, "condition.requirement-not-met");
            }
            return context.player().getInventory().contains(material, amount)
                ? pass() : denied(context, "condition.item", "amount", amount,
                    "material", material.name());
        }
    }

    /** Passes if the player has completed the advancement. An unknown key denies rather than passing. */
    static final class Advancement extends Base {
        private final String key;

        Advancement(String key, @Nullable String deny, Text text) {
            super(deny, text);
            this.key = key;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            NamespacedKey parsed = NamespacedKey.fromString(key.trim().toLowerCase(Locale.ROOT));
            org.bukkit.advancement.Advancement advancement =
                parsed == null ? null : Bukkit.getAdvancement(parsed);
            if (advancement == null) {
                return denied(context, "condition.requirement-not-met");
            }
            return context.player().getAdvancementProgress(advancement).isDone()
                ? pass() : denied(context, "condition.advancement");
        }
    }

    static boolean numericCompare(double left, String operator, double right) {
        return switch (operator.trim().toLowerCase(Locale.ROOT)) {
            case ">", "gt" -> left > right;
            case ">=", "gte", "at-least", "atleast" -> left >= right;
            case "<", "lt" -> left < right;
            case "<=", "lte", "at-most", "atmost" -> left <= right;
            case "!=", "ne", "not" -> left != right;
            default -> left == right;
        };
    }

    /** {@code expires} as epoch millis, an ISO-8601 instant, or a {@code yyyy-MM-dd} date. Defaults to never. */
    private static long parseExpiry(ConfigurationSection section) {
        if (section.isLong("expires") || section.isInt("expires")) {
            return section.getLong("expires");
        }
        String raw = section.getString("expires");
        if (raw == null) {
            return Long.MAX_VALUE;
        }
        try {
            return Instant.parse(raw).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored2) {
                return Long.MAX_VALUE;
            }
        }
    }

    @Nullable
    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String orEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    private static String orDefault(@Nullable String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static Set<String> upperSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(value.toUpperCase(Locale.ROOT));
        }
        return result;
    }
}
