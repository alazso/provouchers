package so.alaz.provouchers.voucher;

/**
 * Soulbound behaviour for a voucher: the item binds to its player and cannot be
 * transferred. Each restriction toggles independently; {@code soulbound: true} in
 * config enables all three.
 *
 * @param blockDrop       the item cannot be dropped
 * @param blockContainers the item cannot be moved into other inventories (chests,
 *                        hoppers, item frames)
 * @param bindOnPickup    an unowned soulbound voucher stamps whoever picks it up
 *                        as its owner
 */
public record SoulboundSpec(boolean blockDrop, boolean blockContainers, boolean bindOnPickup) {

    /** All restrictions enabled, the {@code soulbound: true} shorthand. */
    public static SoulboundSpec all() {
        return new SoulboundSpec(true, true, true);
    }
}
