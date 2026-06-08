package so.alaz.provouchers.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.api.Voucher;
import so.alaz.provouchers.api.VoucherCode;
import so.alaz.provouchers.api.VoucherService;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.strata.api.scheduler.PlatformScheduler;

import java.util.List;
import java.util.Optional;

/**
 * The {@link VoucherService} implementation registered with Bukkit's services
 * manager. It exposes the registry and a programmatic give, delegating item
 * creation to the same async, Folia-safe path the command uses.
 */
public final class VoucherServiceImpl implements VoucherService {

    private final VoucherRegistry registry;
    private final VoucherItemFactory factory;
    private final PlatformScheduler scheduler;

    public VoucherServiceImpl(VoucherRegistry registry, VoucherItemFactory factory,
                              PlatformScheduler scheduler) {
        this.registry = registry;
        this.factory = factory;
        this.scheduler = scheduler;
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
        factory.createItems(voucher, amount, player).thenAccept(items ->
            scheduler.entity(player, () -> player.getInventory().addItem(items.toArray(ItemStack[]::new))
                .values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover))));
        return true;
    }
}
