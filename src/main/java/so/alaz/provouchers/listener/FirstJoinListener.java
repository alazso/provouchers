package so.alaz.provouchers.listener;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import so.alaz.provouchers.give.VoucherGiveService;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.List;

/**
 * Gives the {@code auto-give.first-join} vouchers to a player joining the server for
 * the first time. Ids are resolved at join time, so a reload that adds or fixes a
 * voucher takes effect without a restart; an id that resolves to nothing is logged.
 */
public final class FirstJoinListener implements Listener {

    private final VoucherRegistry registry;
    private final VoucherGiveService giveService;
    private final List<String> voucherIds;
    private final ComponentLogger logger;

    public FirstJoinListener(VoucherRegistry registry, VoucherGiveService giveService,
                             List<String> voucherIds, ComponentLogger logger) {
        this.registry = registry;
        this.giveService = giveService;
        this.voucherIds = List.copyOf(voucherIds);
        this.logger = logger;
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPlayedBefore()) {
            return;
        }
        for (String id : voucherIds) {
            Voucher voucher = registry.getVoucher(id).orElse(null);
            if (voucher == null) {
                logger.warn("auto-give.first-join lists unknown voucher '{}'", id);
                continue;
            }
            giveService.give(event.getPlayer(), voucher, 1);
        }
    }
}
