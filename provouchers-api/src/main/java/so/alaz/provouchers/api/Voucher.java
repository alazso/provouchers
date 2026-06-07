package so.alaz.provouchers.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A loaded voucher definition, as exposed to other plugins. Implementations are
 * provided by ProVouchers and must not be created or implemented by consumers.
 */
@ApiStatus.AvailableSince("0.4.0")
@ApiStatus.NonExtendable
public interface Voucher {

    /** The unique voucher id. */
    String id();

    /** The MiniMessage display name, or {@code null} when the item keeps its own name. */
    @Nullable
    String displayName();

    /** The MiniMessage lore lines. */
    List<String> lore();

    /** The per-player cooldown between redemptions, in seconds ({@code 0} for none). */
    long cooldownSeconds();

    /** The raw expiry value (ISO-8601 or a relative duration), or {@code null} for never. */
    @Nullable
    String expiry();

    /** Whether only the player it was given to may redeem it. */
    boolean ownerOnly();

    /** Whether the item exists for show and cannot be redeemed. */
    boolean unredeemable();

    /** Whether a free-form argument is accepted. */
    boolean hasArgument();
}
