package so.alaz.provouchers.hook;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.platform.Classes;

import java.util.List;
import java.util.Locale;

/**
 * Head Database (Arcaniax) as an {@link ItemHook}: its {@code getItemHead(id)} returns a finished
 * head item, which is exactly the {@link #createItem} contract, so HDB heads flow through the same
 * custom-item path as ItemsAdder/Oraxen/Nexo. {@link #createItem} accepts an optional {@code hdb:}/
 * {@code headdatabase:} prefix so an explicitly-qualified id routes here. Heads resolve once HDB
 * finishes loading its database (shortly after startup); before then lookups simply return
 * {@code null}.
 */
public final class HeadDatabaseItemHook implements ItemHook {

    private static final List<String> PREFIXES = List.of("hdb:", "headdatabase:");

    private final boolean present = Classes.present("me.arcaniax.hdb.api.HeadDatabaseAPI", getClass());

    @Override
    public String name() {
        return "HeadDatabase";
    }

    @Override
    public boolean isAvailable() {
        return present && Bukkit.getPluginManager().isPluginEnabled("HeadDatabase");
    }

    @Override
    @Nullable
    public ItemStack createItem(String id) {
        try {
            String head = headId(id);
            HeadDatabaseAPI api = new HeadDatabaseAPI();
            // Verify the id belongs to HDB first, so an unknown or foreign id returns null rather
            // than a placeholder head that would hijack cross-provider item resolution.
            return api.isHead(head) ? api.getItemHead(head) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Strips a {@code hdb:}/{@code headdatabase:} prefix if present, leaving the bare HDB id. */
    private static String headId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            if (lower.startsWith(prefix)) {
                return id.substring(prefix.length());
            }
        }
        return id;
    }
}
