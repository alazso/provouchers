package so.alaz.provouchers.voucher;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import so.alaz.provouchers.util.Colors;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.SkullBuilder;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.util.Expiry;
import so.alaz.provouchers.util.Placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Builds the physical {@link ItemStack} for a voucher. The base item is, in order
 * of precedence, a custom provider item ({@code custom:}), a player head
 * ({@code skull:}), or a vanilla material. The voucher's name, lore, and appearance
 * extras are applied on top, and the persistent-data stamp (voucher id, plus uid,
 * give time, and owner where they apply) is written.
 *
 * <p>The result is a {@link CompletableFuture} because the player name and UUID skull
 * sources resolve a skin from Mojang off-thread; for every other source the future is
 * already complete. The item may be built off the main thread, so callers must add it
 * to an inventory on the owning region thread.
 */
public final class VoucherItemFactory {

    private final Text text;
    private final VoucherStamp stamp;
    private final ItemResolver items;

    public VoucherItemFactory(Text text, VoucherStamp stamp, ItemResolver items) {
        this.text = text;
        this.stamp = stamp;
        this.items = items;
    }

    /**
     * Creates the items for giving {@code amount} of {@code voucher}. A stackable
     * voucher yields a single stack of {@code amount}; an anti-dupe voucher
     * ({@code stackable: false}) yields {@code amount} separate items, each stamped
     * with its own unique id so they are individually dupe-tracked and do not stack.
     * Names and lore are rendered for {@code viewer} when non-null; for an owner-only or
     * soulbound voucher, {@code viewer} is recorded as the owner.
     */
    public CompletableFuture<List<ItemStack>> createItems(Voucher voucher, int amount,
                                                          @Nullable Player viewer) {
        return buildBase(voucher, viewer).thenApply(base -> {
            long now = System.currentTimeMillis();
            List<ItemStack> result = new ArrayList<>();
            if (voucher.stackable()) {
                ItemStack stack = base.clone();
                stack.setAmount(amount);
                stack.editMeta(meta -> stampCommon(meta, voucher, viewer, now));
                result.add(stack);
            } else {
                for (int i = 0; i < amount; i++) {
                    ItemStack single = base.clone();
                    single.setAmount(1);
                    single.editMeta(meta -> {
                        stampCommon(meta, voucher, viewer, now);
                        stamp.setUid(meta, VoucherStamp.newUid());
                    });
                    result.add(single);
                }
            }
            return result;
        });
    }

    /**
     * Builds a display-only copy of the voucher's appearance for previews and GUIs. The
     * item carries no stamp, so it is not a redeemable voucher.
     */
    public CompletableFuture<ItemStack> buildDisplay(Voucher voucher, @Nullable Player viewer) {
        return buildBase(voucher, viewer);
    }

    private static String viewerName(@Nullable Player viewer) {
        return viewer != null ? viewer.getName() : "";
    }

    private void stampCommon(ItemMeta meta, Voucher voucher, @Nullable Player viewer, long now) {
        stamp.stamp(meta, voucher.id());
        // The give time is stamped only for a relative expiry, which measures from it; its per-give
        // value stops items from separate gives stacking.
        if (Expiry.isRelative(voucher.expiry())) {
            stamp.setGivenAt(meta, now);
        }
        // A soulbound voucher gets an owner at give time, so bind-on-pickup (which binds only
        // unowned items) does not rebind it.
        if ((voucher.ownerOnly() || voucher.soulbound() != null) && viewer != null) {
            stamp.setOwner(meta, viewer.getUniqueId());
        }
    }

    /**
     * Builds a defined reward item ({@code items:} map) for {@code viewer}, carrying
     * {@code amount}. Uses the voucher icon build path, without a stamp.
     */
    public CompletableFuture<ItemStack> createDefinedItem(DefinedItem defined, int amount,
                                                          @Nullable Player viewer) {
        return buildAppearance(defined.displayName(), defined.lore(), defined.item(), null, null, viewer)
            .thenApply(item -> {
                item.setAmount(amount);
                return item;
            });
    }

    private CompletableFuture<ItemStack> buildBase(Voucher voucher, @Nullable Player viewer) {
        return buildAppearance(voucher.displayName(), voucher.lore(), voucher.item(),
            voucher.expiry(), voucher.id(), viewer);
    }

    /**
     * Builds a single decorated item from an appearance: a custom provider item, a skull,
     * or a vanilla material, with name and lore rendered for {@code viewer}. Without a
     * display name, a custom or skull item keeps its own name and a material item uses
     * {@code fallbackName} when that is non-null.
     */
    private CompletableFuture<ItemStack> buildAppearance(@Nullable String displayName, List<String> lore,
                                                         VoucherItem spec, @Nullable String expiryRaw,
                                                         @Nullable String fallbackName, @Nullable Player viewer) {
        ItemStack custom = items.custom(spec.customItem());
        if (custom != null) {
            custom.setAmount(1);
            decorate(custom, displayName, lore, spec, expiryRaw, viewer, false);
            return CompletableFuture.completedFuture(custom);
        }
        SkullSpec skull = spec.skull();
        if (skull != null) {
            return buildSkull(skull, 1).thenApply(head -> {
                decorate(head, displayName, lore, spec, expiryRaw, viewer, true);
                return head;
            });
        }
        return CompletableFuture.completedFuture(
            buildMaterial(spec, displayName, lore, expiryRaw, fallbackName, viewer));
    }

    private ItemStack buildMaterial(VoucherItem spec, @Nullable String displayName, List<String> lore,
                                    @Nullable String expiryRaw, @Nullable String fallbackName,
                                    @Nullable Player viewer) {
        Material material = Materials.resolve(spec.material());
        if (!material.isItem()) {
            throw new IllegalArgumentException(
                "material '" + material.name() + "' is not an obtainable item");
        }
        String viewerName = viewerName(viewer);
        String expiry = Expiry.describe(expiryRaw);
        String name = displayName != null ? displayName : fallbackName;
        ItemBuilder builder = new ItemBuilder(material).amount(1);
        if (name != null) {
            builder.name(text.render(
                Placeholders.apply(Placeholders.applyExpiry(name, expiry), viewerName, null), viewer));
        }
        if (!lore.isEmpty()) {
            builder.lore(text.render(
                Placeholders.applyAll(Placeholders.applyExpiryAll(lore, expiry), viewerName, null), viewer));
        }
        ItemStack item = builder.build();
        item.editMeta(meta -> applyExtras(meta, spec, true));
        return item;
    }

    /**
     * Applies the appearance extras shared by every build path: custom model data (when
     * {@code applyModelData}), enchantments, glow, unbreakable, damage, trim, item model,
     * hidden tooltip, and dye color. Unknown enchantment and trim keys are skipped.
     * Enchantments already glint, so the glow is added only to an item without them.
     */
    private static void applyExtras(ItemMeta meta, VoucherItem spec, boolean applyModelData) {
        if (applyModelData && spec.customModelData() != null) {
            meta.setCustomModelData(spec.customModelData());
        }
        spec.enchantments().forEach((key, level) -> {
            Enchantment enchant = resolveEnchant(key);
            if (enchant != null) {
                meta.addEnchant(enchant, level, true);
            }
        });
        if (spec.glow() && spec.enchantments().isEmpty() && !meta.hasEnchants()) {
            ItemBuilder.applyGlow(meta);
        }
        if (spec.unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (spec.damage() != null && meta instanceof Damageable damageable) {
            damageable.setDamage(spec.damage());
        }
        if (spec.trim() != null && meta instanceof ArmorMeta armor) {
            TrimMaterial trimMaterial = resolve(Registry.TRIM_MATERIAL, spec.trim().material());
            TrimPattern trimPattern = resolve(Registry.TRIM_PATTERN, spec.trim().pattern());
            if (trimMaterial != null && trimPattern != null) {
                armor.setTrim(new ArmorTrim(trimMaterial, trimPattern));
            }
        }
        if (spec.itemModel() != null) {
            meta.setItemModel(NamespacedKey.fromString(spec.itemModel()));
        }
        if (spec.hideTooltip()) {
            meta.setHideTooltip(true);
        }
        if (spec.color() != null) {
            applyColor(meta, Colors.parse(spec.color()));
        }
    }

    /** Applies a dye color to leather armor, a potion, or a map; other items ignore it. */
    private static void applyColor(ItemMeta meta, Color color) {
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        } else if (meta instanceof PotionMeta potion) {
            potion.setColor(color);
        } else if (meta instanceof MapMeta map) {
            map.setColor(color);
        }
    }

    @Nullable
    private static Enchantment resolveEnchant(String key) {
        return resolve(Registry.ENCHANTMENT, key);
    }

    /** Looks a value up in a registry by its namespaced key, or {@code null} when malformed or unknown. */
    @Nullable
    private static <T extends org.bukkit.Keyed> T resolve(Registry<T> registry, String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        return namespaced == null ? null : registry.get(namespaced);
    }

    private CompletableFuture<ItemStack> buildSkull(SkullSpec skull, int amount) {
        CompletableFuture<ItemStack> head = switch (skull.source()) {
            case TEXTURE -> CompletableFuture.completedFuture(SkullBuilder.fromTexture(skull.value()));
            case URL -> CompletableFuture.completedFuture(SkullBuilder.fromUrl(skull.value()));
            case NAME -> SkullBuilder.fromName(skull.value());
            case UUID -> SkullBuilder.fromUuid(UUID.fromString(skull.value()));
        };
        return head.thenApply(item -> {
            item.setAmount(amount);
            return item;
        });
    }

    /**
     * Applies a name, lore, and the appearance extras onto an already-built base item (a
     * custom provider item or a skull). The display name is overridden only when one is set.
     */
    private void decorate(ItemStack item, @Nullable String displayName, List<String> lore,
                          VoucherItem spec, @Nullable String expiryRaw, @Nullable Player viewer,
                          boolean applyModelData) {
        String viewerName = viewerName(viewer);
        String expiry = Expiry.describe(expiryRaw);
        item.editMeta(meta -> {
            if (displayName != null) {
                meta.displayName(text.render(
                    Placeholders.apply(Placeholders.applyExpiry(displayName, expiry), viewerName, null), viewer)
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (!lore.isEmpty()) {
                meta.lore(text.render(
                    Placeholders.applyAll(Placeholders.applyExpiryAll(lore, expiry), viewerName, null), viewer).stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            }
            applyExtras(meta, spec, applyModelData);
        });
    }
}
