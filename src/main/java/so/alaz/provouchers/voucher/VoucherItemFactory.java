package so.alaz.provouchers.voucher;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 * ({@code skull:}), or a vanilla material. The voucher's name, lore, and glow are
 * applied on top, and the persistent-data stamp (id, batch, give time, owner) is
 * written.
 *
 * <p>The result is a {@link CompletableFuture} because some skull sources (player
 * name or UUID) resolve a skin from Mojang off-thread. For every other source the
 * future is already complete. The returned item may be built off the main thread,
 * so callers must add it to an inventory on the owning region thread.
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
     * Names and lore are rendered for {@code viewer} when non-null; for an owner-only
     * voucher, {@code viewer} is recorded as the owner.
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
     * Builds a display-only copy of the voucher's appearance (name, lore, glow) with
     * no stamp, for previews and GUIs. Never give this item out: it is not a redeemable
     * voucher.
     */
    public CompletableFuture<ItemStack> buildDisplay(Voucher voucher, @Nullable Player viewer) {
        return buildBase(voucher, viewer);
    }

    /** The viewer's name for placeholder substitution, or empty when an item is built with no viewer. */
    private static String viewerName(@Nullable Player viewer) {
        return viewer != null ? viewer.getName() : "";
    }

    private void stampCommon(ItemMeta meta, Voucher voucher, @Nullable Player viewer, long now) {
        stamp.stamp(meta, voucher.id());
        // Anchor the give time only for relative expiry (e.g. "30d"), which measures from it.
        // Stamping it otherwise gives every batch a different value, so a stackable voucher would
        // not stack across separate gives.
        if (Expiry.isRelative(voucher.expiry())) {
            stamp.setGivenAt(meta, now);
        }
        // Soulbound vouchers carry an owner like owner-only ones, so bind-on-pickup and
        // ownership checks have a subject from the moment the item is given.
        if ((voucher.ownerOnly() || voucher.soulbound() != null) && viewer != null) {
            stamp.setOwner(meta, viewer.getUniqueId());
        }
    }

    /**
     * Builds a defined reward item ({@code items:} map) for {@code viewer}, carrying
     * {@code amount}. Shares the voucher icon build path: base precedence (custom, skull,
     * material), name and lore rendering, glow, and model data all behave identically.
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
     * or a vanilla material, with name and lore rendered for {@code viewer}. With no display
     * name, {@code fallbackName} is used; when that is also null the base item keeps its own.
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
     * {@code applyModelData}), enchantments, and the glow glint. Real enchantments already
     * glint, so the hidden glow is only added when there are none.
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
    }

    @Nullable
    private static Enchantment resolveEnchant(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        return namespaced == null ? null : Registry.ENCHANTMENT.get(namespaced);
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
     * Applies a name, lore, and glow onto an already-built base item (a custom provider
     * item or a skull). The display name is only overridden when one is set; custom model
     * data is applied only when {@code applyModelData}.
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
