package so.alaz.provouchers.hook;

/**
 * Base type for an integration with a third-party plugin. Implementations detect their backing
 * plugin and report {@link #isAvailable()}; consumers never reference the third-party plugin
 * directly.
 *
 * <p>Implementations hold no provider-typed fields, only a class-presence flag. Provider classes are
 * referenced only inside method bodies, which are guarded by {@link #isAvailable()} and catch linkage
 * errors, so a missing or incompatible plugin degrades to {@code null}/{@code false} instead of failing.
 */
public interface Hook {

    /** Name of the backing plugin (e.g. "LuckPerms"). */
    String name();

    /** {@code true} if this hook can be used right now (backing plugin present and ready). */
    boolean isAvailable();
}
