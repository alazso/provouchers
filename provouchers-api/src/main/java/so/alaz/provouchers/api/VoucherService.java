package so.alaz.provouchers.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/**
 * The ProVouchers API entry point. Obtain it from Bukkit's services manager once
 * ProVouchers has enabled:
 *
 * <pre>{@code
 * VoucherService vouchers = getServer().getServicesManager().load(VoucherService.class);
 * if (vouchers != null) {
 *     vouchers.give(player, "crate_key", 1);
 * }
 * }</pre>
 *
 * <p>To react to redemptions, listen for the events in {@code so.alaz.provouchers.api.event}.
 */
@ApiStatus.AvailableSince("0.4.0")
@ApiStatus.NonExtendable
public interface VoucherService {

    /** The voucher with this id, if loaded. */
    Optional<Voucher> getVoucher(String id);

    /** All loaded voucher ids, sorted. */
    List<String> voucherIds();

    /** The code matching this typed input (honouring case-sensitivity), if loaded. */
    Optional<VoucherCode> getCode(String input);

    /** The number of loaded vouchers. */
    int voucherCount();

    /** The number of loaded codes. */
    int codeCount();

    /**
     * Gives {@code amount} copies of a voucher to {@code player}. Item creation and
     * delivery happen asynchronously and Folia-safely.
     *
     * @return {@code false} if no voucher with that id is loaded
     */
    boolean give(Player player, String voucherId, int amount);
}
