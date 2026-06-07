package so.alaz.provouchers.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import so.alaz.provouchers.api.Voucher;

/**
 * Fired synchronously before an item voucher is redeemed, after ProVouchers' own
 * checks pass and before the item is consumed. Cancel it to veto the redemption.
 * This is the policy seam for anti-cheat, spending limits, and similar gates.
 *
 * <p>Fired on the redeeming player's region thread.
 */
@ApiStatus.AvailableSince("0.4.0")
public class VoucherPreRedeemEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Voucher voucher;
    private boolean cancelled;

    @ApiStatus.Internal
    public VoucherPreRedeemEvent(Player player, Voucher voucher) {
        this.player = player;
        this.voucher = voucher;
    }

    /** The player redeeming the voucher. */
    public Player getPlayer() {
        return player;
    }

    /** The voucher being redeemed. */
    public Voucher getVoucher() {
        return voucher;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
