package so.alaz.provouchers.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.api.VoucherCode;

/**
 * Fired synchronously before a code is redeemed, after ProVouchers' own checks
 * pass and before the use-limit check. Cancel it to veto the redemption.
 *
 * <p>Fired on the redeeming player's thread.
 */
@ApiStatus.AvailableSince("0.4.0")
public class VoucherCodePreRedeemEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final VoucherCode code;
    private final String argument;
    private boolean cancelled;

    @ApiStatus.Internal
    public VoucherCodePreRedeemEvent(Player player, VoucherCode code, @Nullable String argument) {
        this.player = player;
        this.code = code;
        this.argument = argument;
    }

    /** The player redeeming the code. */
    public Player getPlayer() {
        return player;
    }

    /** The code being redeemed. */
    public VoucherCode getCode() {
        return code;
    }

    /** The argument passed with the code, or {@code null}. */
    @Nullable
    public String getArgument() {
        return argument;
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
