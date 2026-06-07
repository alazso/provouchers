package so.alaz.provouchers.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A typeable redemption code, as exposed to other plugins. Implementations are
 * provided by ProVouchers and must not be created or implemented by consumers.
 */
@ApiStatus.AvailableSince("0.4.0")
@ApiStatus.NonExtendable
public interface VoucherCode {

    /** The literal text players type. */
    String code();

    /** Whether matching is case-sensitive. */
    boolean caseSensitive();

    /** The global redemption cap across all players, or {@code -1} for unlimited. */
    int maxUses();

    /** How many times a single player may redeem it. */
    int usesPerPlayer();

    /** The raw expiry value, or {@code null} for never. */
    @Nullable
    String expiry();

    /** Whether a free-form argument is accepted. */
    boolean hasArgument();
}
