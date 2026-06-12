package so.alaz.provouchers.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.config.ConfigManager;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.Text;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * The {@code /voucher fromhand} confirmation menu (PV-15). Shows the held item rebuilt
 * from its serialized form, the exact item the voucher will produce, so the admin
 * verifies the round trip before anything is written. Confirming writes
 * {@code vouchers/<id>.yml} and registers it; cancelling (or closing) writes nothing.
 */
public final class FromhandGui {

    private final GuiManager guiManager;
    private final ConfigManager configManager;
    private final Messages messages;
    private final Text text;
    private final File vouchersDir;

    public FromhandGui(GuiManager guiManager, ConfigManager configManager, Messages messages,
                       Text text, File dataFolder) {
        this.guiManager = guiManager;
        this.configManager = configManager;
        this.messages = messages;
        this.text = text;
        this.vouchersDir = new File(dataFolder, "vouchers");
    }

    /** Whether a voucher or code file with this id already exists on disk. */
    public boolean fileExists(String id) {
        return new File(vouchersDir, id + ".yml").isFile();
    }

    /** Opens the confirm menu; {@code preview} is the item rebuilt from {@code serialized}. */
    public void open(Player admin, String id, String serialized, ItemStack preview) {
        Gui gui = ChestGui.builder(3)
            .title("<gradient:#FFD700:#FF8A00>Confirm voucher: " + id + "</gradient>")
            .button(11, Button.of(confirmIcon(admin, id), click -> {
                confirm(click.getPlayer(), id, serialized);
                return GuiAction.none();
            }))
            .button(13, Button.display(preview))
            .button(15, Button.of(cancelIcon(admin), click -> {
                click.getPlayer().closeInventory();
                send(click.getPlayer(), "command.fromhand.cancelled");
                return GuiAction.none();
            }))
            .build();
        guiManager.open(gui, admin);
    }

    private void confirm(Player admin, String id, String serialized) {
        admin.closeInventory();
        File file = new File(vouchersDir, id + ".yml");
        try {
            Files.createDirectories(vouchersDir.toPath());
            // CREATE_NEW makes the existence check and the write atomic, so two admins
            // confirming the same id cannot overwrite each other.
            Files.writeString(file.toPath(), template(id, serialized), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ex) {
            send(admin, "command.fromhand.exists", "id", id);
            return;
        } catch (IOException ex) {
            send(admin, "command.fromhand.failed");
            return;
        }
        configManager.reloadOne(id);
        send(admin, "command.fromhand.created", "id", id, "file", "vouchers/" + id + ".yml");
    }

    private String template(String id, String serialized) {
        return """
            # Created with /voucher fromhand. The base item is the captured one, full fidelity.
            # Add rewards, conditions, and behaviour; apply edits with /voucher reload %s.
            item:
              custom: "%s"
            rewards:
              - "message: <green>You redeemed %s!"
            """.formatted(id, serialized, id);
    }

    private ItemStack confirmIcon(Player admin, String id) {
        return new ItemBuilder(Material.LIME_CONCRETE)
            .name(text.render("<green><b>Create voucher</b>", admin))
            .lore(List.of(
                text.render("<gray>Writes <yellow>vouchers/" + id + ".yml</yellow>", admin),
                text.render("<gray>and loads it immediately.", admin)))
            .build();
    }

    private ItemStack cancelIcon(Player admin) {
        return new ItemBuilder(Material.RED_CONCRETE)
            .name(text.render("<red><b>Cancel</b>", admin))
            .lore(List.of(text.render("<gray>No file will be written.", admin)))
            .build();
    }

    private void send(Player player, String key, Object... placeholders) {
        player.sendMessage(text.render(messages.get(player, key, placeholders), player));
    }
}
