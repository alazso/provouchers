package so.alaz.provouchers.hook;

import org.bukkit.OfflinePlayer;

/** Economy operations behind one interface, backing the currency reward and economy condition. */
public interface EconomyHook extends Hook {

    /** {@code true} if the player can afford the amount. */
    boolean has(OfflinePlayer player, double amount);

    /** Withdraws the amount; returns {@code true} on success. */
    boolean withdraw(OfflinePlayer player, double amount);

    /** Deposits the amount; returns {@code true} on success. */
    boolean deposit(OfflinePlayer player, double amount);
}
