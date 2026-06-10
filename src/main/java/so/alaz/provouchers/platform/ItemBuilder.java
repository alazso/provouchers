package so.alaz.provouchers.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;
import java.util.List;

/**
 * Fluent {@link ItemStack} builder with Adventure names and lore. Display name and lore have
 * the default italic styling stripped (the usual menu-item expectation). {@link #build()}
 * requires a running server (item meta).
 */
public final class ItemBuilder {

    private final Material material;
    private int amount = 1;
    @Nullable private Component name;
    private List<Component> lore = List.of();
    private boolean glow;

    public ItemBuilder(Material material) {
        this.material = material;
    }

    public ItemBuilder amount(int amount) {
        this.amount = amount;
        return this;
    }

    public ItemBuilder name(Component name) {
        this.name = name;
        return this;
    }

    public ItemBuilder lore(List<Component> lore) {
        this.lore = lore;
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemStack build() {
        ItemStack item = new ItemStack(material, amount);
        item.editMeta(meta -> {
            if (name != null) {
                meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            }
            if (glow) {
                applyGlow(meta);
            }
        });
        return item;
    }

    /** Gives an item the menu glow: an enchant glint with the enchantment hidden. */
    public static void applyGlow(ItemMeta meta) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    /**
     * The reference scheme for a full-fidelity item: {@code serialized:<base64>}. Unlike a material
     * or a {@code provider:id} reference, this carries the entire item (enchants, name, lore, model
     * data, attributes, components, and any other NBT), so it round-trips exactly.
     */
    public static final String SERIALIZED_PREFIX = "serialized:";

    /** Whether the reference is a {@link #SERIALIZED_PREFIX serialized} item reference. */
    public static boolean isSerialized(String reference) {
        return reference.startsWith(SERIALIZED_PREFIX);
    }

    /** Serializes an item to a {@code serialized:<base64>} reference, preserving all of its data. */
    public static String serialize(ItemStack item) {
        return SERIALIZED_PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /**
     * Rebuilds the exact item from a {@code serialized:<base64>} reference, or {@code null} if it is
     * malformed or was written for an incompatible server version (the caller then degrades).
     */
    @Nullable
    public static ItemStack deserialize(String reference) {
        try {
            byte[] bytes = Base64.getDecoder().decode(reference.substring(SERIALIZED_PREFIX.length()));
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
