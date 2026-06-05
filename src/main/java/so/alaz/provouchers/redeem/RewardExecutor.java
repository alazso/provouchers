package so.alaz.provouchers.redeem;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.util.Tokens;
import so.alaz.strata.api.scheduler.PlatformScheduler;
import so.alaz.strata.api.text.TextRenderer;

import java.time.Duration;
import java.util.List;

/**
 * Runs reward actions for a player. Command dispatch and broadcasts are routed
 * through Strata's scheduler so the plugin stays Folia-safe; messages, titles,
 * action bars, and sounds act directly on the player. Tokens such as
 * {@code %player%}, {@code {arg}}, and {@code {random:min-max}} are substituted
 * first, then MiniMessage and PlaceholderAPI are resolved by Strata's renderer.
 */
public final class RewardExecutor {

    private static final Title.Times TITLE_TIMES = Title.Times.times(
        Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500));

    private final PlatformScheduler scheduler;
    private final TextRenderer text;

    public RewardExecutor(PlatformScheduler scheduler, TextRenderer text) {
        this.scheduler = scheduler;
        this.text = text;
    }

    /** Runs every reward line for {@code player}, substituting {@code arg} where present. */
    public void execute(Player player, List<RewardLine> rewards, @Nullable String arg) {
        for (RewardLine reward : rewards) {
            execute(player, reward, arg);
        }
    }

    private void execute(Player player, RewardLine reward, @Nullable String arg) {
        String payload = Tokens.apply(reward.payload(), player.getName(), arg);
        switch (reward.type()) {
            case CONSOLE_COMMAND -> scheduler.global(() ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload));
            case PLAYER_COMMAND -> scheduler.entity(player, () -> player.performCommand(payload));
            case MESSAGE -> player.sendMessage(text.render(payload, player));
            case BROADCAST -> {
                Component message = text.render(payload, player);
                scheduler.global(() -> Bukkit.broadcast(message));
            }
            case TITLE -> {
                String[] parts = payload.split("\\|", 2);
                Component title = text.render(parts[0], player);
                Component subtitle = parts.length > 1 ? text.render(parts[1], player) : Component.empty();
                player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
            }
            case ACTIONBAR -> player.sendActionBar(text.render(payload, player));
            case SOUND -> playSound(player, payload);
        }
    }

    private void playSound(Player player, String payload) {
        String[] parts = payload.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        Key key = Key.key(parts[0]);
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        player.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
