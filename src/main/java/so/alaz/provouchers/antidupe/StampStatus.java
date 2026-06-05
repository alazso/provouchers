package so.alaz.provouchers.antidupe;

/**
 * The result of validating a voucher item's stamp against persistent storage.
 */
public enum StampStatus {

    /** The stamp has not been redeemed before; the redemption may proceed. */
    VALID,
    /** The stamp has already been redeemed; this is a duplicate and must be rejected. */
    DUPLICATE,
    /** The stamp could not be checked (for example a storage error); treat with caution. */
    UNKNOWN
}
