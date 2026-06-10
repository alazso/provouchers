package so.alaz.provouchers.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import so.alaz.provouchers.platform.Classes;

import java.util.List;

/**
 * WorldGuard-backed {@link RegionHook}. Queries are guarded and wrapped, so a missing WorldGuard
 * degrades to an empty result instead of throwing.
 */
public final class WorldGuardRegionHook implements RegionHook {

    private final boolean present = Classes.present("com.sk89q.worldguard.WorldGuard", getClass());

    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean isAvailable() {
        return present && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public List<String> regionsAt(Location location) {
        try {
            World world = location.getWorld();
            if (world == null) {
                return List.of();
            }
            RegionManager manager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return List.of();
            }
            return manager.getApplicableRegions(BukkitAdapter.asBlockVector(location)).getRegions().stream()
                .map(ProtectedRegion::getId)
                .toList();
        } catch (Throwable ignored) {
            return List.of();
        }
    }
}
