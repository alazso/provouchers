package so.alaz.provouchers.voucher;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.hook.ItemHook;
import so.alaz.provouchers.platform.ItemBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Resolves item references to {@link ItemStack}s. A reference is a vanilla material
 * name, a {@code provider:id} custom item served by an {@link ItemHook} (ItemsAdder,
 * Oraxen, Nexo, HeadDatabase), or a {@code serialized:<base64>} full-fidelity item.
 */
public final class ItemResolver {

    private final HookRegistry hooks;

    public ItemResolver(HookRegistry hooks) {
        this.hooks = hooks;
    }

    /**
     * Resolves a custom provider item, or {@code null} when {@code reference} is
     * null, names no provider item, or no available hook can build it.
     */
    @Nullable
    public ItemStack custom(@Nullable String reference) {
        if (reference == null) {
            return null;
        }
        if (ItemBuilder.isSerialized(reference)) {
            return ItemBuilder.deserialize(reference);
        }
        CustomItemRef ref = CustomItemRef.parse(reference);
        List<ItemHook> providers = hooks.all(ItemHook.class);
        if (ref.providerHint() != null) {
            // A qualified reference resolves only through the named provider, with the bare id, and
            // never falls back to another provider.
            String name = canonicalProvider(ref.providerHint());
            for (ItemHook provider : providers) {
                if (provider.isAvailable() && provider.name().equalsIgnoreCase(name)) {
                    return provider.createItem(ref.id());
                }
            }
            return null;
        }
        for (ItemHook provider : providers) {
            if (provider.isAvailable()) {
                ItemStack item = provider.createItem(reference);
                if (item != null) {
                    return item;
                }
            }
        }
        return null;
    }

    /** Whether the provider named by a custom-item hint (aliases resolved) is installed and available. */
    public boolean providerAvailable(String hint) {
        String name = canonicalProvider(hint);
        for (ItemHook provider : hooks.all(ItemHook.class)) {
            if (provider.isAvailable() && provider.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalProvider(String hint) {
        return switch (hint.toLowerCase(Locale.ROOT)) {
            case "ia" -> "itemsadder";
            case "hdb" -> "headdatabase";
            default -> hint;
        };
    }

    /**
     * Resolves an item to grant as a reward: a provider item when one matches,
     * otherwise a vanilla material. The returned stack carries {@code amount}
     * (clamped to at least 1).
     *
     * @throws IllegalArgumentException if neither a provider item nor an obtainable material resolves
     */
    public ItemStack give(String reference, int amount) {
        int count = Math.max(1, amount);
        ItemStack custom = custom(reference);
        if (custom != null) {
            custom.setAmount(count);
            return custom;
        }
        Material material = Materials.resolve(reference);
        if (!material.isItem()) {
            throw new IllegalArgumentException("'" + reference + "' is not an obtainable item");
        }
        return new ItemStack(material, count);
    }
}
