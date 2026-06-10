package so.alaz.provouchers.hook;

import org.bukkit.Location;

import java.util.List;

/** Region queries behind one interface (WorldGuard), backing the region condition. */
public interface RegionHook extends Hook {

    /** Ids of all regions covering the location. */
    List<String> regionsAt(Location location);
}
