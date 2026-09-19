package so.alaz.provouchers.voucher;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Resolves a configured material name to a {@link Material}. Resolution uses
 * {@code Material.getMaterial}, which works without a running server (the registry
 * does not), so the parser can validate materials at load and in tests.
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
