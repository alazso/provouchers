package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

/**
 * Optional cosmetic effects played to the redeemer on a successful redeem. Currently a
 * sound (a {@code "key [volume] [pitch]"} string), which may be {@code null}.
 */
public record VoucherEffects(@Nullable String sound) {

    /** Whether no effect is configured. */
    public boolean isEmpty() {
        return sound == null || sound.isBlank();
    }
}
