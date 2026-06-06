package so.alaz.provouchers.voucher;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Resolves a configured material name to a {@link Material}. Name resolution is
 * server-independent (it goes through the enum, not the runtime registry) so it
 * can be used both at config-load time for validation and at item-build time.
 */
public final class Materials {

    private Materials() {
    }

    /**
     * Resolves {@code name} (an enum name such as {@code PAPER}, optionally
     * lower-cased or namespaced like {@code minecraft:paper}) to a material.
     *
     * @throws IllegalArgumentException if the name does not match any material
     */
    public static Material resolve(String name) {
        String token = name;
        int colon = token.lastIndexOf(':');
        if (colon >= 0) {
            token = token.substring(colon + 1);
        }
        Material material = Material.getMaterial(token.toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException("unknown material '" + name + "'");
        }
        return material;
    }
}
