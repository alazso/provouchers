package so.alaz.provouchers.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.api.Voucher;

/**
 * Fired after an item voucher has been successfully redeemed: the item was
 * consumed, the duplicate check passed, and rewards were granted. Not cancellable;
 * this is a post-commit notification.
 *
 * <p>Fired on the redeeming player's region thread. The {@code batchId} and
 * {@code nonce} identify the exact item that was redeemed, for auditing.
 */
@ApiStatus.AvailableSince("0.4.0")
public class VoucherRedeemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Voucher voucher;
    private final String batchId;
    private final String nonce;

    @ApiStatus.Internal
    public VoucherRedeemEvent(Player player, Voucher voucher, @Nullable String batchId,
                             @Nullable String nonce) {
        this.player = player;
        this.voucher = voucher;
        this.batchId = batchId;
        this.nonce = nonce;
    }

    /** The player who redeemed the voucher. */
    public Player getPlayer() {
        return player;
    }

    /** The voucher that was redeemed. */
    public Voucher getVoucher() {
        return voucher;
    }

    /** The redeemed item's batch id, or {@code null} for an unstamped item. */
    @Nullable
    public String getBatchId() {
        return batchId;
    }

    /** The redeemed item's per-item nonce, or {@code null} for an unstamped item. */
    @Nullable
    public String getNonce() {
        return nonce;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
