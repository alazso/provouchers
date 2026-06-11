package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

/**
 * Optional cosmetic effects played to the redeemer on a successful redeem: a sound
 * (a {@code "key [volume] [pitch]"} string) and/or a particle (a Bukkit
 * {@link org.bukkit.Particle} name spawned at the player). Either may be {@code null}.
 */
public record VoucherEffects(@Nullable String sound, @Nullable String particle) {

    /** Whether neither a sound nor a particle is configured. */
    public boolean isEmpty() {
        return (sound == null || sound.isBlank()) && (particle == null || particle.isBlank());
    }
}
