package so.alaz.provouchers.hook;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.metrics.ErrorReports;
import so.alaz.provouchers.platform.Classes;

import java.util.List;
import java.util.Locale;

/**
 * Head Database (Arcaniax) as an {@link ItemHook}. {@link #createItem} accepts a bare HDB id or one
 * prefixed with {@code hdb:} or {@code headdatabase:}, and returns {@code null} until HDB has loaded
 * its database, shortly after startup.
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
            // getItemHead returns a placeholder head for an unknown id, which would stop the other
            // item providers from resolving it.
            return api.isHead(head) ? api.getItemHead(head) : null;
        } catch (Throwable ex) {
            ErrorReports.report("hook.headdatabase", ex);
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
