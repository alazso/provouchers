package so.alaz.provouchers.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import so.alaz.provouchers.cooldown.CooldownService;

/** Loads a player's persisted voucher cooldowns into memory on join and unloads them on quit. */
public final class CooldownLoadListener implements Listener {

    private final CooldownService cooldowns;

    public CooldownLoadListener(CooldownService cooldowns) {
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.hydrate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.unload(event.getPlayer().getUniqueId());
    }
}
