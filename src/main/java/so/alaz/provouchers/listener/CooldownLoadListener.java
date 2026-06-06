package so.alaz.provouchers.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import so.alaz.provouchers.cooldown.CooldownService;

/**
 * Loads a player's persisted voucher cooldowns into memory when they join, so
 * cooldowns survive restarts.
 */
public final class CooldownLoadListener implements Listener {

    private final CooldownService cooldowns;

    public CooldownLoadListener(CooldownService cooldowns) {
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.hydrate(event.getPlayer().getUniqueId());
    }
}
