package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.condition.ConditionRegistry;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.reward.RewardSet;
import so.alaz.provouchers.voucher.CustomItemRef;
import so.alaz.provouchers.voucher.ItemResolver;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads voucher and code definitions from the plugin data folder into a
 * {@link VoucherRegistry}. Each {@code vouchers/*.yml} file is one voucher and
 * each {@code codes/*.yml} file is one code. Files that fail validation are
 * collected and reported rather than aborting the whole load.
 */
public final class ConfigManager {

    private final File dataFolder;
    private final VoucherRegistry registry;
    private final ConditionRegistry conditions;
    private final ItemResolver items;

    public ConfigManager(File dataFolder, VoucherRegistry registry, ConditionRegistry conditions,
                         ItemResolver items) {
        this.dataFolder = dataFolder;
        this.registry = registry;
        this.conditions = conditions;
        this.items = items;
    }

    /**
     * Clears and reloads the registry from disk.
     *
     * @return the per-file errors encountered; empty when every file loaded cleanly
     */
    public List<String> reload() {
        registry.clear();
        List<String> errors = new ArrayList<>();
        loadVouchers(errors);
        loadCodes(errors);
        return errors;
    }

    /**
     * Reloads a single voucher and/or code file by its id (the file name without extension),
     * leaving every other loaded entry in place. A file that now fails to parse keeps its
     * previously loaded version and reports the error.
     *
     * @return the per-file errors, or {@code null} when no voucher or code file by that id exists
     */
    @Nullable
    public List<String> reloadOne(String id) {
        File voucherFile = new File(new File(dataFolder, "vouchers"), id + ".yml");
        File codeFile = new File(new File(dataFolder, "codes"), id + ".yml");
        if (!voucherFile.isFile() && !codeFile.isFile()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        if (voucherFile.isFile()) {
            loadVoucherFile(voucherFile, errors);
        }
        if (codeFile.isFile()) {
            loadCodeFile(codeFile, errors);
        }
        return errors;
    }

    private void loadVouchers(List<String> errors) {
        for (File file : ymlFiles(new File(dataFolder, "vouchers"))) {
            loadVoucherFile(file, errors);
        }
    }

    private void loadVoucherFile(File file, List<String> errors) {
        String id = stripExtension(file.getName());
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Voucher voucher = VoucherParser.parseVoucher(yaml, id);
            requireKnownConditions(voucher.conditionMaps());
            registry.register(voucher);
            warnUnavailableProvider(file.getName(), voucher.item().customItem(), errors);
            warnVoucherPlaceholders(file.getName(), voucher, errors);
        } catch (VoucherParseException ex) {
            errors.add(file.getName() + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            errors.add(file.getName() + ": " + ex.getClass().getSimpleName() + " "
                + ex.getMessage());
        }
    }

    private void loadCodes(List<String> errors) {
        for (File file : ymlFiles(new File(dataFolder, "codes"))) {
            loadCodeFile(file, errors);
        }
    }

    private void loadCodeFile(File file, List<String> errors) {
        String id = stripExtension(file.getName());
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            VoucherCode code = VoucherParser.parseCode(yaml, id);
            requireKnownConditions(code.conditionMaps());
            registry.register(code);
            warnCodePlaceholders(file.getName(), code, errors);
        } catch (VoucherParseException ex) {
            errors.add(file.getName() + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            errors.add(file.getName() + ": " + ex.getClass().getSimpleName() + " "
                + ex.getMessage());
        }
    }

    /**
     * Rejects a condition whose {@code type} is not registered, so a typo or a removed type fails
     * loudly at load (and the voucher/code does not load) rather than silently dropping the gate at
     * redeem time, which would let the restriction fail open.
     */
    private void requireKnownConditions(List<Map<String, Object>> conditionMaps) {
        for (Map<String, Object> condition : conditionMaps) {
            if (condition.get("type") instanceof String type && !conditions.isRegistered(type)) {
                throw new VoucherParseException("unknown condition type '" + type + "'");
            }
        }
    }

    /** Notes, without blocking the load, when a voucher's custom item names an uninstalled provider. */
    private void warnUnavailableProvider(String fileName, @Nullable String customRef, List<String> errors) {
        if (customRef == null) {
            return;
        }
        CustomItemRef ref = CustomItemRef.parse(customRef);
        if (ref.providerHint() != null && !items.providerAvailable(ref.providerHint())) {
            errors.add(fileName + ": item provider '" + ref.providerHint()
                + "' is not installed; the item falls back to its material");
        }
    }

    /** Notes, without blocking the load, when a voucher uses deprecated curly-brace placeholders. */
    private static void warnVoucherPlaceholders(String fileName, Voucher voucher, List<String> errors) {
        List<String> texts = new ArrayList<>(voucher.lore());
        addIfPresent(texts, voucher.displayName());
        addIfPresent(texts, voucher.confirmMessage());
        addRewardAndConditionText(texts, voucher.rewards(), voucher.randomRewards(), voucher.conditionMaps());
        if (texts.stream().anyMatch(ConfigManager::usesDeprecatedPlaceholder)) {
            errors.add(deprecationNotice(fileName));
        }
    }

    /** Notes, without blocking the load, when a code uses deprecated curly-brace placeholders. */
    private static void warnCodePlaceholders(String fileName, VoucherCode code, List<String> errors) {
        List<String> texts = new ArrayList<>();
        addRewardAndConditionText(texts, code.rewards(), code.randomRewards(), code.conditionMaps());
        if (texts.stream().anyMatch(ConfigManager::usesDeprecatedPlaceholder)) {
            errors.add(deprecationNotice(fileName));
        }
    }

    private static void addRewardAndConditionText(List<String> out, List<RewardLine> rewards,
                                                  List<RewardSet> random, List<Map<String, Object>> conditions) {
        for (RewardLine reward : rewards) {
            out.add(reward.payload());
        }
        for (RewardSet set : random) {
            for (RewardLine reward : set.rewards()) {
                out.add(reward.payload());
            }
        }
        for (Map<String, Object> condition : conditions) {
            for (Object value : condition.values()) {
                if (value instanceof String text) {
                    out.add(text);
                }
            }
        }
    }

    private static void addIfPresent(List<String> out, @Nullable String value) {
        if (value != null) {
            out.add(value);
        }
    }

    private static boolean usesDeprecatedPlaceholder(String text) {
        return text.contains("{player}") || text.contains("{arg}") || text.contains("{random:");
    }

    private static String deprecationNotice(String fileName) {
        return fileName + ": uses deprecated curly-brace placeholders ({player}, {arg}, {random:..}); switch to "
            + "%player%, %arg%, and %random:..% (the curly-brace form still works for now)";
    }

    private static File[] ymlFiles(File folder) {
        if (!folder.isDirectory()) {
            return new File[0];
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        return files == null ? new File[0] : files;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
