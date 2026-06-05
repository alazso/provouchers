package so.alaz.provouchers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import so.alaz.strata.api.StrataApi;

import static net.kyori.adventure.text.Component.text;

/**
 * Plugin entry point. Verifies the runtime prerequisites (Java 25, the Strata
 * shared library), then wires up the voucher services.
 */
public final class ProVouchersPlugin extends JavaPlugin {

    private static final int MINIMUM_JAVA_FEATURE = 25;

    @Override
    public void onEnable() {
        if (Runtime.version().feature() < MINIMUM_JAVA_FEATURE) {
            getComponentLogger().error(text(
                "ProVouchers requires Java " + MINIMUM_JAVA_FEATURE + " or newer, but the server "
                    + "is running Java " + Runtime.version().feature() + ". Disabling.",
                NamedTextColor.RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!StrataApi.isAvailable()) {
            getComponentLogger().error(text(
                "Strata is not available. Install the Strata plugin and ensure it loads before "
                    + "ProVouchers. Disabling.",
                NamedTextColor.RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // TODO(M1): load configuration, build the voucher registry, register commands and listeners.
        getComponentLogger().info(text("ProVouchers enabled.", NamedTextColor.GOLD));
    }

    @Override
    public void onDisable() {
        getComponentLogger().info(text("ProVouchers disabled.", NamedTextColor.GOLD));
    }
}
