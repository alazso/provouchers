package so.alaz.provouchers.metrics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.reward.RewardType;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherItem;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wires ProVouchers usage metrics through the bundled bStats and FastStats providers
 * (shaded and relocated into the plugin jar).
 *
 * <p>The bStats id and FastStats token below identify ProVouchers itself (one per
 * plugin, set by the author), not the server. A provider is only enabled once its
 * credential is filled in, and the whole feature respects {@code metrics.enabled}
 * in {@code config.yml}.
 *
 * <p>Every chart is provider-agnostic: each {@link MetricChart} routes to the native
 * chart on each enabled backend, so a single declaration reports to both. Charts split
 * into config-shape snapshots (read from the registry on each poll) and runtime activity
 * (read from {@link MetricCounters}, which the redeem pipeline feeds).
 */
public final class VoucherMetrics {

    /** ProVouchers' bStats service id. */
    private static final int BSTATS_ID = 31826;

    /** ProVouchers' FastStats project token. */
    private static final String FASTSTATS_TOKEN = "48a6198b8f241382cb9989b18d039776";

    /** Optional integrations whose presence on the server we report adoption of. */
    private static final List<String> INTEGRATIONS = List.of(
        "Vault", "PlaceholderAPI", "MiniPlaceholders", "WorldGuard", "LuckPerms",
        "Oraxen", "ItemsAdder", "Nexo", "HeadDatabase");

    private VoucherMetrics() {
    }

    /**
     * Starts metrics submission, or returns {@code null} when metrics are disabled
     * in config or no provider credential is configured. The caller keeps the handle
     * and calls {@link Metrics#shutdown()} on plugin disable.
     */
    @Nullable
    public static Metrics start(JavaPlugin plugin, VoucherRegistry registry, MetricCounters counters,
                                Supplier<String> backend) {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) {
            return null;
        }
        MetricsBuilder builder = new MetricsBuilder(plugin);
        boolean anyProvider = false;
        if (BSTATS_ID > 0) {
            builder.enable(MetricProvider.BSTATS, Integer.toString(BSTATS_ID));
            anyProvider = true;
        }
        if (!FASTSTATS_TOKEN.isEmpty()) {
            builder.enable(MetricProvider.FASTSTATS, FASTSTATS_TOKEN);
            builder.errorTracking(true);
            anyProvider = true;
        }
        if (!anyProvider) {
            return null;
        }
        builder
            // Environment
            .addChart(MetricChart.string("storage_backend", backend))
            .addChart(MetricChart.stringList("active_integrations", VoucherMetrics::activeIntegrations))
            // Configuration shape
            .addChart(MetricChart.number("loaded_vouchers", registry::voucherCount))
            .addChart(MetricChart.number("loaded_codes", registry::codeCount))
            .addChart(MetricChart.string("voucher_count_bucket", () -> bucket(registry.voucherCount())))
            .addChart(MetricChart.string("code_count_bucket", () -> bucket(registry.codeCount())))
            .addChart(MetricChart.string("most_common_reward", () -> mostCommonReward(registry)))
            .addChart(MetricChart.stringList("reward_types_used", () -> rewardTypes(registry)))
            .addChart(MetricChart.stringList("condition_types_used", () -> conditionTypes(registry)))
            .addChart(MetricChart.stringList("item_providers_used", () -> itemProviders(registry)))
            .addChart(MetricChart.bool("uses_random_rewards", () -> anyRandomRewards(registry)))
            .addChart(MetricChart.bool("uses_cooldowns", () -> anyCooldowns(registry)))
            .addChart(MetricChart.bool("uses_expiry", () -> anyExpiry(registry)))
            .addChart(MetricChart.bool("uses_conditions", () -> anyConditions(registry)))
            // Runtime activity (session totals)
            .addChart(MetricChart.number("redemptions", counters::totalRedemptions))
            .addChart(MetricChart.number("voucher_redemptions", counters::voucherRedemptions))
            .addChart(MetricChart.number("code_redemptions", counters::codeRedemptions))
            .addChart(MetricChart.number("dupes_blocked", counters::duplicatesBlocked))
            .addChart(MetricChart.number("condition_denials", counters::conditionDenials))
            .addChart(MetricChart.string("top_reward_granted", counters::topRewardGranted));
        return builder.start();
    }

    private static List<String> activeIntegrations() {
        List<String> active = new ArrayList<>();
        for (String name : INTEGRATIONS) {
            if (Bukkit.getPluginManager().isPluginEnabled(name)) {
                active.add(name);
            }
        }
        return active;
    }

    /** A privacy-friendly size bucket so the pie shows a distribution, not raw counts. */
    private static String bucket(int count) {
        if (count <= 0) {
            return "0";
        }
        if (count <= 5) {
            return "1-5";
        }
        if (count <= 20) {
            return "6-20";
        }
        if (count <= 50) {
            return "21-50";
        }
        if (count <= 100) {
            return "51-100";
        }
        return "100+";
    }

    private static String mostCommonReward(VoucherRegistry registry) {
        Map<String, Integer> counts = new HashMap<>();
        for (String type : rewardTypes(registry)) {
            counts.merge(type, 1, Integer::sum);
        }
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");
    }

    private static List<String> rewardTypes(VoucherRegistry registry) {
        List<String> types = new ArrayList<>();
        for (Voucher voucher : registry.vouchers()) {
            collectRewardTypes(voucher.rewards(), voucher.randomRewards(), types);
        }
        for (VoucherCode code : registry.codes()) {
            collectRewardTypes(code.rewards(), code.randomRewards(), types);
        }
        return types;
    }

    private static void collectRewardTypes(List<RewardLine> always, List<RewardSet> random,
                                           List<String> out) {
        for (RewardLine line : always) {
            out.add(label(line.type()));
        }
        for (RewardSet set : random) {
            for (RewardLine line : set.rewards()) {
                out.add(label(line.type()));
            }
        }
    }

    private static List<String> conditionTypes(VoucherRegistry registry) {
        List<String> types = new ArrayList<>();
        for (Voucher voucher : registry.vouchers()) {
            collectConditionTypes(voucher.conditionMaps(), types);
        }
        for (VoucherCode code : registry.codes()) {
            collectConditionTypes(code.conditionMaps(), types);
        }
        return types;
    }

    private static void collectConditionTypes(List<Map<String, Object>> maps, List<String> out) {
        for (Map<String, Object> map : maps) {
            Object type = map.get("type");
            if (type != null) {
                out.add(type.toString().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static List<String> itemProviders(VoucherRegistry registry) {
        List<String> providers = new ArrayList<>();
        for (Voucher voucher : registry.vouchers()) {
            providers.add(providerLabel(voucher.item()));
        }
        return providers;
    }

    private static String providerLabel(VoucherItem item) {
        if (item.skull() != null) {
            return "player-head";
        }
        String custom = item.customItem();
        if (custom != null && custom.contains(":")) {
            return custom.substring(0, custom.indexOf(':')).toLowerCase(Locale.ROOT);
        }
        if (custom != null && !custom.isBlank()) {
            return "custom";
        }
        return "vanilla";
    }

    private static boolean anyRandomRewards(VoucherRegistry registry) {
        for (Voucher voucher : registry.vouchers()) {
            if (!voucher.randomRewards().isEmpty()) {
                return true;
            }
        }
        for (VoucherCode code : registry.codes()) {
            if (!code.randomRewards().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyCooldowns(VoucherRegistry registry) {
        for (Voucher voucher : registry.vouchers()) {
            if (voucher.cooldownSeconds() > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyExpiry(VoucherRegistry registry) {
        for (Voucher voucher : registry.vouchers()) {
            if (voucher.expiry() != null) {
                return true;
            }
        }
        for (VoucherCode code : registry.codes()) {
            if (code.expiry() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyConditions(VoucherRegistry registry) {
        for (Voucher voucher : registry.vouchers()) {
            if (!voucher.conditionMaps().isEmpty()) {
                return true;
            }
        }
        for (VoucherCode code : registry.codes()) {
            if (!code.conditionMaps().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String label(RewardType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
