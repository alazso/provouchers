package so.alaz.provouchers.service;

import org.bukkit.entity.Player;
import so.alaz.provouchers.api.Voucher;
import so.alaz.provouchers.api.VoucherCode;
import so.alaz.provouchers.api.VoucherService;
import so.alaz.provouchers.give.VoucherGiveService;
import so.alaz.provouchers.stash.StashService;
import so.alaz.provouchers.stash.StashSource;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link VoucherService} implementation registered with Bukkit's services
 * manager. It exposes the registry and a programmatic give, delegating item
 * creation to the same async, Folia-safe path the command uses.
 */
public final class VoucherServiceImpl implements VoucherService {

    private final VoucherRegistry registry;
    private final VoucherGiveService giveService;
    private final StashService stashService;

    public VoucherServiceImpl(VoucherRegistry registry, VoucherGiveService giveService,
                              StashService stashService) {
        this.registry = registry;
        this.giveService = giveService;
        this.stashService = stashService;
    }

    @Override
    public Optional<Voucher> getVoucher(String id) {
        return registry.getVoucher(id).map(voucher -> (Voucher) voucher);
    }

    @Override
    public List<String> voucherIds() {
        return registry.voucherIds();
    }

    @Override
    public Optional<VoucherCode> getCode(String input) {
        return Optional.<VoucherCode>ofNullable(registry.findCode(input));
    }

    @Override
    public int voucherCount() {
        return registry.voucherCount();
    }

    @Override
    public int codeCount() {
        return registry.codeCount();
    }

    @Override
    public boolean give(Player player, String voucherId, int amount) {
        so.alaz.provouchers.voucher.Voucher voucher = registry.getVoucher(voucherId).orElse(null);
        if (voucher == null) {
            return false;
        }
        giveService.give(player, voucher, amount);
        return true;
    }

    @Override
    public boolean stash(UUID player, String voucherId, int amount) {
        if (player == null || amount < 1) {
            return false;
        }
        so.alaz.provouchers.voucher.Voucher voucher = registry.getVoucher(voucherId).orElse(null);
        if (voucher == null) {
            return false;
        }
        stashService.stash(player, voucher.id(), amount, null, StashSource.API);
        return true;
    }
}

