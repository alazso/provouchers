package so.alaz.provouchers.config;

/**
 * Thrown when a voucher or code file fails validation. The message names the
 * offending key and the reason so operators can fix the file without guesswork.
 */
public class VoucherParseException extends RuntimeException {

    public VoucherParseException(String message) {
        super(message);
    }

    public VoucherParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
