package so.alaz.provouchers.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.api.VoucherCode;

/**
 * Fired after a code has been successfully redeemed: the use limits passed and
 * rewards were granted. Not cancellable; this is a post-commit notification.
 *
 * <p>Fired on the redeeming player's region thread.
 */
@ApiStatus.AvailableSince("0.4.0")
public class VoucherCodeRedeemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final VoucherCode code;
    private final String argument;

    @ApiStatus.Internal
    public VoucherCodeRedeemEvent(Player player, VoucherCode code, @Nullable String argument) {
        this.player = player;
        this.code = code;
        this.argument = argument;
    }

    /** The player who redeemed the code. */
    public Player getPlayer() {
        return player;
    }

    /** The code that was redeemed. */
    public VoucherCode getCode() {
        return code;
    }

    /** The argument passed with the code, or {@code null}. */
    @Nullable
    public String getArgument() {
        return argument;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
