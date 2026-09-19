package so.alaz.provouchers.antidupe;

/**
 * The result of claiming a voucher item's unique id with {@link DupeDetector#claim}.
 */
public enum ClaimOutcome {

    /** This claim is stored: the id now counts as redeemed by the claiming player. */
    CLAIMED,
    /** The id was already claimed by another redemption, so this claim was not stored. */
    TAKEN,
    /** Storage failed, so this claim may or may not have been stored and must be given back. */
    UNKNOWN
}
