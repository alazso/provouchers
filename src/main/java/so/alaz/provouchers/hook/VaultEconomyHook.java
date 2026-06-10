package so.alaz.provouchers.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Vault-backed {@link EconomyHook}. Resolves the economy provider Vault registered in Bukkit's
 * services manager, so any Vault-compatible economy works. Lookups are guarded and wrapped, so a
 * missing Vault, or Vault present with no economy plugin behind it, degrades to unavailable/no-op.
 */
public final class VaultEconomyHook implements EconomyHook {

    private final boolean present = Classes.present("net.milkbowl.vault.economy.Economy", getClass());

    @Override
    public String name() {
        return "Vault";
    }

    @Override
    public boolean isAvailable() {
        return present && economy() != null;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Nullable
    private Economy economy() {
        try {
            var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            return registration != null ? registration.getProvider() : null;
        } catch (Throwable ignored) {
            // A broken or version-incompatible Vault degrades to unavailable.
            return null;
        }
    }
}
