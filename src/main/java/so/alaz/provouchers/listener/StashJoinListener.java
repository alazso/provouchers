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
 * Tells a joining player how many rewards wait in their Stash, when any do. The count is the number of
 * cards the menu shows, not stored rows; it is read off-thread, and the greeting is sent on the player's
 * thread.
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
