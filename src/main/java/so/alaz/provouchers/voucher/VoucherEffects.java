package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

/**
 * Optional cosmetic effects played to the redeemer on a successful redeem: a sound
 * (a {@code "key [volume] [pitch]"} string) and/or a firework. Either may be {@code null}.
 */
public record VoucherEffects(@Nullable String sound, @Nullable FireworkSpec firework) {

    /** Whether no effect is configured. */
    public boolean isEmpty() {
        return (sound == null || sound.isBlank()) && firework == null;
    }
}
