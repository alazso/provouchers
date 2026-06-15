package so.alaz.provouchers.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.gui.StashGui;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.Text;

import java.util.List;

/**
 * The {@code /stash} command: opens a player's Stash of claimable virtual vouchers. Registered only
 * when {@code stash.enabled} is set; its aliases come from config.
 */
public final class StashCommand {

    private static final String PERM_STASH = "provouchers.stash";

    private final StashGui stashGui;
    private final Text text;
    private final Messages messages;

    public StashCommand(StashGui stashGui, Text text, Messages messages) {
        this.stashGui = stashGui;
        this.text = text;
        this.messages = messages;
    }

    /** Registers {@code /stash} (with {@code aliases}) for {@code plugin}. Call during {@code onEnable}. */
    public void register(Plugin plugin, List<String> aliases) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register(buildTree(), "Open your Stash of claimable rewards", aliases));
    }

    private LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal("stash")
            .requires(source -> source.getSender().hasPermission(PERM_STASH))
            .executes(ctx -> {
                Player player = asPlayer(ctx.getSource());
                if (player != null) {
                    stashGui.open(player);
                } else {
                    ctx.getSource().getSender().sendMessage(
                        text.render(messages.get(null, "command.stash.players-only")));
                }
                return Command.SINGLE_SUCCESS;
            })
            .build();
    }

    @Nullable
    private static Player asPlayer(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player player) {
            return player;
        }
        return source.getSender() instanceof Player player ? player : null;
    }
}
