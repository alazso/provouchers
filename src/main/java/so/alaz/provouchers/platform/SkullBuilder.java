package so.alaz.provouchers.platform;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Builds {@code PLAYER_HEAD} items from the stable skull sources, with no third-party plugin.
 *
 * <p>{@link #fromTexture} and {@link #fromUrl} are synchronous and need no network: the head
 * carries a texture property and the client fetches the image from Mojang's CDN, so they are
 * the reliable choice for permanent icons. {@link #fromName} and {@link #fromUuid} resolve the
 * player's current skin from Mojang off-thread (cached), so they suit dynamic "this player's
 * head" displays; the returned future completes off the main thread, so schedule any inventory
 * or entity work back on the right thread yourself.
 *
 * <p>Resolved textures are cached; failed lookups are not, so a transient failure can be retried.
 */
public final class SkullBuilder {

    private SkullBuilder() {
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "provouchers-skull");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<String, CompletableFuture<String>> TEXTURE_CACHE =
        new ConcurrentHashMap<>();

    /** A head carrying the given base64 {@code textures} property value (the {@code eyJ0ZXh0...} form). */
    public static ItemStack fromTexture(String base64) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        // Deterministic profile id so identical textures yield identical, stackable heads.
        PlayerProfile profile = Bukkit.createProfile(
            UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    /** A head for a Minecraft texture URL (e.g. {@code http://textures.minecraft.net/texture/...}). */
    public static ItemStack fromUrl(String url) {
        return fromTexture(base64FromUrl(url));
    }

    /**
     * A head for the name's current skin, resolved from Mojang off-thread and cached. Completes
     * with a blank head if the name cannot be resolved.
     */
    public static CompletableFuture<ItemStack> fromName(String name) {
        return resolveTexture("name:" + name.toLowerCase(Locale.ROOT), () -> {
            PlayerProfile profile = Bukkit.createProfile(name);
            profile.complete(true);
            return textureValue(profile);
        }).thenApply(SkullBuilder::headOrBlank);
    }

    /**
     * A head for the uuid's current skin, resolved from Mojang off-thread and cached. Completes
     * with a blank head if the uuid cannot be resolved.
     */
    public static CompletableFuture<ItemStack> fromUuid(UUID uuid) {
        return resolveTexture("uuid:" + uuid, () -> {
            PlayerProfile profile = Bukkit.createProfile(uuid);
            profile.complete(true);
            return textureValue(profile);
        }).thenApply(SkullBuilder::headOrBlank);
    }

    private static ItemStack headOrBlank(@Nullable String texture) {
        return texture != null ? fromTexture(texture) : new ItemStack(Material.PLAYER_HEAD);
    }

    private static String base64FromUrl(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static CompletableFuture<String> resolveTexture(String key, Supplier<String> loader) {
        return TEXTURE_CACHE.computeIfAbsent(key, cacheKey ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    return loader.get();
                } catch (RuntimeException ex) {
                    return null;
                }
            }, EXECUTOR).whenComplete((value, error) -> {
                // Do not cache a failed lookup, so a transient Mojang failure can be retried.
                if (value == null) {
                    TEXTURE_CACHE.remove(cacheKey);
                }
            }));
    }

    @Nullable
    private static String textureValue(PlayerProfile profile) {
        return profile.getProperties().stream()
            .filter(property -> property.getName().equals("textures"))
            .map(ProfileProperty::getValue)
            .findFirst()
            .orElse(null);
    }
}
