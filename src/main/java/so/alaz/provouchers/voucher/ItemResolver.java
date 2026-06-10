package so.alaz.provouchers.voucher;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.hook.ItemHook;

import java.util.List;

/**
 * Resolves item references to {@link ItemStack}s, shared by voucher icon building
 * and item rewards. A reference is either a vanilla material name or a
 * {@code provider:id} custom item served by an {@link ItemHook} (ItemsAdder,
 * Oraxen, Nexo, HeadDatabase).
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
        CustomItemRef ref = CustomItemRef.parse(reference);
        List<ItemHook> providers = hooks.all(ItemHook.class);
        if (ref.providerHint() != null) {
            for (ItemHook provider : providers) {
                if (provider.isAvailable() && provider.name().equalsIgnoreCase(ref.providerHint())) {
                    ItemStack item = provider.createItem(ref.id());
                    if (item != null) {
                        return item;
                    }
                }
            }
        }
        for (ItemHook provider : providers) {
            if (!provider.isAvailable()) {
                continue;
            }
            ItemStack item = provider.createItem(reference);
            if (item == null && ref.providerHint() != null) {
                item = provider.createItem(ref.id());
            }
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    /**
     * Resolves an item to grant as a reward: a provider item when one matches,
     * otherwise a vanilla material. The returned stack carries {@code amount}
     * (clamped to at least 1).
     *
     * @throws IllegalArgumentException if neither a provider item nor a material resolves
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
