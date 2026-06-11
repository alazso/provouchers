package so.alaz.provouchers.voucher;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.antidupe.VoucherStamp;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.SkullBuilder;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.reward.RewardDescriber;
import so.alaz.provouchers.util.Expiry;
import so.alaz.provouchers.util.Tokens;

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

    /** The header shown above the generated reward summary when a voucher sets show-rewards. */
    private static final String REWARDS_HEADER = "<gray>Rewards:";

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

    /** The viewer's name for token substitution, or empty when an item is built with no viewer. */
    private static String viewerName(@Nullable Player viewer) {
        return viewer != null ? viewer.getName() : "";
    }

    /**
     * The voucher's configured lore, plus a generated reward summary appended when
     * {@code show-rewards} is set (guaranteed rewards then weighted random sets with
     * their chances). Returns the raw MiniMessage lines, before token and text rendering.
     */
    private List<String> loreLines(Voucher voucher) {
        List<String> lore = new ArrayList<>(voucher.lore());
        if (!voucher.showRewards()) {
            return lore;
        }
        List<String> preview = new ArrayList<>();
        for (String reward : RewardDescriber.describeAll(voucher.rewards())) {
            preview.add("<dark_gray>- <gray>" + reward);
        }
        for (String reward : RewardDescriber.describeRandom(voucher.randomRewards())) {
            preview.add("<dark_gray>- <gray>" + reward);
        }
        if (preview.isEmpty()) {
            return lore;
        }
        if (!lore.isEmpty()) {
            lore.add("");
        }
        lore.add(REWARDS_HEADER);
        lore.addAll(preview);
        return lore;
    }

    private void stampCommon(ItemMeta meta, Voucher voucher, @Nullable Player viewer, long now) {
        stamp.stamp(meta, voucher.id());
        // Anchor the give time only for relative expiry (e.g. "30d"), which measures from it.
        // Stamping it otherwise gives every batch a different value, so a stackable voucher would
        // not stack across separate gives.
        if (Expiry.isRelative(voucher.expiry())) {
            stamp.setGivenAt(meta, now);
        }
        if (voucher.ownerOnly() && viewer != null) {
            stamp.setOwner(meta, viewer.getUniqueId());
        }
    }

    private CompletableFuture<ItemStack> buildBase(Voucher voucher, @Nullable Player viewer) {
        ItemStack custom = items.custom(voucher.item().customItem());
        if (custom != null) {
            custom.setAmount(1);
            decorate(custom, voucher, viewer, false);
            return CompletableFuture.completedFuture(custom);
        }
        SkullSpec skull = voucher.item().skull();
        if (skull != null) {
            return buildSkull(skull, 1).thenApply(head -> {
                decorate(head, voucher, viewer, true);
                return head;
            });
        }
        return CompletableFuture.completedFuture(buildMaterial(voucher, 1, viewer));
    }

    private ItemStack buildMaterial(Voucher voucher, int amount, @Nullable Player viewer) {
        Material material = Materials.resolve(voucher.item().material());
        if (!material.isItem()) {
            throw new IllegalArgumentException(
                "material '" + material.name() + "' is not an obtainable item");
        }
        String viewerName = viewerName(viewer);
        String name = voucher.displayName() != null ? voucher.displayName() : voucher.id();
        ItemBuilder builder = new ItemBuilder(material)
            .amount(amount)
            .glow(voucher.item().glow())
            .name(text.render(Tokens.apply(name, viewerName, null), viewer));
        List<String> lore = loreLines(voucher);
        if (!lore.isEmpty()) {
            builder.lore(text.render(Tokens.applyAll(lore, viewerName, null), viewer));
        }
        ItemStack item = builder.build();
        Integer customModelData = voucher.item().customModelData();
        if (customModelData != null) {
            item.editMeta(meta -> meta.setCustomModelData(customModelData));
        }
        return item;
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
     * Applies the voucher's name, lore, and glow onto an already-built base item
     * (a custom provider item or a skull). The display name is only overridden when
     * the voucher sets one; custom model data is applied only when {@code applyModelData}.
     */
    private void decorate(ItemStack item, Voucher voucher, @Nullable Player viewer, boolean applyModelData) {
        String viewerName = viewerName(viewer);
        item.editMeta(meta -> {
            if (voucher.displayName() != null) {
                meta.displayName(text.render(Tokens.apply(voucher.displayName(), viewerName, null), viewer)
                    .decoration(TextDecoration.ITALIC, false));
            }
            List<String> lore = loreLines(voucher);
            if (!lore.isEmpty()) {
                meta.lore(text.render(Tokens.applyAll(lore, viewerName, null), viewer).stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            }
            if (voucher.item().glow() && !meta.hasEnchants()) {
                ItemBuilder.applyGlow(meta);
            }
            Integer customModelData = voucher.item().customModelData();
            if (applyModelData && customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
        });
    }
}
