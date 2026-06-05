package so.alaz.provouchers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherCode;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads voucher and code definitions from the plugin data folder into a
 * {@link VoucherRegistry}. Each {@code vouchers/*.yml} file is one voucher and
 * each {@code codes/*.yml} file is one code. Files that fail validation are
 * collected and reported rather than aborting the whole load.
 */
public final class ConfigManager {

    private final File dataFolder;
    private final VoucherRegistry registry;

    public ConfigManager(File dataFolder, VoucherRegistry registry) {
        this.dataFolder = dataFolder;
        this.registry = registry;
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
                registry.register(code);
            } catch (VoucherParseException ex) {
                errors.add(file.getName() + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                errors.add(file.getName() + ": " + ex.getClass().getSimpleName() + " "
                    + ex.getMessage());
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
