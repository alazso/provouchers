package so.alaz.provouchers.condition;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.hook.EconomyHook;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.hook.PermissionHook;
import so.alaz.provouchers.hook.RegionHook;
import so.alaz.provouchers.platform.Text;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The hook-backed built-in conditions (economy, rank, region) and their registration. Each resolves
 * its provider per evaluation through the {@link HookRegistry}, so a provider that is missing or
 * goes away denies gracefully instead of throwing.
 */
final class HookConditions {

    private HookConditions() {
    }

    static void registerAll(ConditionRegistry registry, Text text, HookRegistry hooks) {
        registry.register("economy", section ->
            new Economy(section.getDouble("amount"), section.getString("deny"), text, hooks));
        registry.register("rank", section ->
            new Rank(lowerSet(section.getStringList("groups")), section.getString("deny"), text, hooks));
        registry.register("region", section ->
            new Region(lowerSet(section.getStringList("regions")), section.getString("deny"), text, hooks));
    }

    /** Passes if the player can afford the amount. Denies gracefully without an economy provider. */
    static final class Economy extends BuiltinConditions.Base {
        private final double amount;
        private final HookRegistry hooks;

        Economy(double amount, @Nullable String deny, Text text, HookRegistry hooks) {
            super(deny, text);
            this.amount = amount;
            this.hooks = hooks;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            EconomyHook economy = hooks.get(EconomyHook.class);
            if (economy == null) {
                return denied(context, "<red>Economy is unavailable.");
            }
            return economy.has(context.player(), amount)
                ? pass() : denied(context, "<red>You can't afford this.");
        }
    }

    /** Passes if the player is in one of the required groups. Needs a group-aware provider. */
    static final class Rank extends BuiltinConditions.Base {
        private final Set<String> groups;
        private final HookRegistry hooks;

        Rank(Set<String> groups, @Nullable String deny, Text text, HookRegistry hooks) {
            super(deny, text);
            this.groups = groups;
            this.hooks = hooks;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            PermissionHook perms = hooks.get(PermissionHook.class);
            Set<String> playerGroups = new HashSet<>();
            if (perms != null) {
                String primary = perms.primaryGroup(context.player());
                if (primary != null) {
                    playerGroups.add(primary.toLowerCase(Locale.ROOT));
                }
                for (String group : perms.groups(context.player())) {
                    playerGroups.add(group.toLowerCase(Locale.ROOT));
                }
            }
            return playerGroups.stream().anyMatch(groups::contains)
                ? pass() : denied(context, "<red>You lack the required rank.");
        }
    }

    /** Passes if the player's location is in one of the required regions. Needs a region provider. */
    static final class Region extends BuiltinConditions.Base {
        private final Set<String> regions;
        private final HookRegistry hooks;

        Region(Set<String> regions, @Nullable String deny, Text text, HookRegistry hooks) {
            super(deny, text);
            this.regions = regions;
            this.hooks = hooks;
        }

        @Override
        public ConditionResult test(ConditionContext context) {
            RegionHook regionHook = hooks.get(RegionHook.class);
            if (regionHook == null) {
                return denied(context, "<red>Region support is unavailable.");
            }
            // Region ids compare case-insensitively: WorldGuard lowercases ids, and the configured
            // set is lowercased at construction.
            return regionHook.regionsAt(context.player().getLocation()).stream()
                .map(id -> id.toLowerCase(Locale.ROOT))
                .anyMatch(regions::contains)
                ? pass() : denied(context, "<red>You're not in the required region.");
        }
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
