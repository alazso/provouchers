package so.alaz.provouchers.voucher;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory store of loaded vouchers and codes. Lookups are case-insensitive
 * for voucher ids; codes honour their own case-sensitivity flag. The registry is
 * cleared and repopulated on reload.
 */
public final class VoucherRegistry {

    private final Map<String, Voucher> vouchers = new ConcurrentHashMap<>();
    private final Map<String, VoucherCode> codes = new ConcurrentHashMap<>();

    /** Registers a voucher, replacing any existing voucher with the same id. */
    public void register(Voucher voucher) {
        vouchers.put(voucher.id().toLowerCase(Locale.ROOT), voucher);
    }

    /** Registers a code, replacing any existing code with the same key. */
    public void register(VoucherCode code) {
        codes.put(code.key(), code);
    }

    /** The voucher with this id, if loaded. */
    public Optional<Voucher> getVoucher(String id) {
        return Optional.ofNullable(vouchers.get(id.toLowerCase(Locale.ROOT)));
    }

    /** The code matching this typed input, honouring per-code case-sensitivity. */
    @Nullable
    public VoucherCode findCode(String input) {
        VoucherCode caseInsensitive = codes.get(input.toLowerCase(Locale.ROOT));
        if (caseInsensitive != null && caseInsensitive.matches(input)) {
            return caseInsensitive;
        }
        VoucherCode exact = codes.get(input);
        if (exact != null && exact.matches(input)) {
            return exact;
        }
        for (VoucherCode code : codes.values()) {
            if (code.matches(input)) {
                return code;
            }
        }
        return null;
    }

    /** All loaded voucher ids, in no particular order. */
    public List<String> voucherIds() {
        return vouchers.values().stream().map(Voucher::id).sorted().toList();
    }

    /** All loaded codes. */
    public Collection<VoucherCode> codes() {
        return List.copyOf(codes.values());
    }

    public int voucherCount() {
        return vouchers.size();
    }

    public int codeCount() {
        return codes.size();
    }

    /** Removes everything; used at the start of a reload. */
    public void clear() {
        vouchers.clear();
        codes.clear();
    }
}
