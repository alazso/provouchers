package so.alaz.provouchers.antidupe;

/**
 * The result of validating a voucher item's stamp against persistent storage.
 */
public enum StampStatus {

    /** The stamp has not been redeemed before; the redemption may proceed. */
    VALID,
    /** The stamp has already been redeemed; the redemption must be rejected. */
    DUPLICATE,
    /** The stamp could not be checked, for example because storage failed. */
    UNKNOWN
}
