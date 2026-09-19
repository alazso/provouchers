package so.alaz.provouchers.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.api.Voucher;

/**
 * Fired after a voucher has been redeemed and its rewards granted: once per item, and
 * once per copy granted by a Stash claim. Not cancellable.
 *
 * <p>Fired on the redeeming player's region thread. The {@code uid} identifies the
 * exact item that was redeemed; it is {@code null} for a stackable (non-anti-dupe)
 * voucher and for a Stash claim.
 */
@ApiStatus.AvailableSince("0.4.0")
public class VoucherRedeemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Voucher voucher;
    private final String uid;

    @ApiStatus.Internal
    public VoucherRedeemEvent(Player player, Voucher voucher, @Nullable String uid) {
        this.player = player;
        this.voucher = voucher;
        this.uid = uid;
    }

    /** The player who redeemed the voucher. */
    public Player getPlayer() {
        return player;
    }

    /** The voucher that was redeemed. */
    public Voucher getVoucher() {
        return voucher;
    }

    /** The redeemed item's unique id, or {@code null} for a stackable (non-anti-dupe) voucher or a Stash claim. */
    @Nullable
    public String getUid() {
        return uid;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
