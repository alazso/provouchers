package so.alaz.provouchers.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.stash.StashService;

/**
 * Tells a joining player when rewards are waiting in their Stash. The count is read off-thread; the
 * greeting is sent back on the player's thread, and only when something is actually waiting.
 */
public final class StashJoinListener implements Listener {

    private final StashService stashService;
    private final Scheduler scheduler;
    private final Text text;
    private final Messages messages;

    public StashJoinListener(StashService stashService, Scheduler scheduler, Text text, Messages messages) {
        this.stashService = stashService;
        this.scheduler = scheduler;
        this.text = text;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stashService.count(player.getUniqueId(), count -> {
            if (count > 0) {
                scheduler.entity(player, () ->
                    text.send(player, messages.get(player, "stash.notify", "count", count)));
            }
        });
    }
}
