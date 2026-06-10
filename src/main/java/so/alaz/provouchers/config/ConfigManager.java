package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import so.alaz.provouchers.condition.ConditionRegistry;
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

    public ConfigManager(File dataFolder, VoucherRegistry registry, ConditionRegistry conditions) {
        this.dataFolder = dataFolder;
        this.registry = registry;
        this.conditions = conditions;
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

    private void loadVouchers(List<String> errors) {
        File folder = new File(dataFolder, "vouchers");
        for (File file : ymlFiles(folder)) {
            String id = stripExtension(file.getName());
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                Voucher voucher = VoucherParser.parseVoucher(yaml, id);
                requireKnownConditions(voucher.conditionMaps());
                registry.register(voucher);
            } catch (VoucherParseException ex) {
                errors.add(file.getName() + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                errors.add(file.getName() + ": " + ex.getClass().getSimpleName() + " "
                    + ex.getMessage());
            }
        }
    }

    private void loadCodes(List<String> errors) {
        File folder = new File(dataFolder, "codes");
        for (File file : ymlFiles(folder)) {
            String id = stripExtension(file.getName());
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                VoucherCode code = VoucherParser.parseCode(yaml, id);
                requireKnownConditions(code.conditionMaps());
                registry.register(code);
            } catch (VoucherParseException ex) {
                errors.add(file.getName() + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                errors.add(file.getName() + ": " + ex.getClass().getSimpleName() + " "
                    + ex.getMessage());
            }
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
