package so.alaz.provouchers.hook;

/**
 * Base type for an integration with a third-party plugin. Implementations detect their backing
 * plugin and report {@link #isAvailable()}; consumers never reference the third-party plugin
 * directly. That indirection is what prevents breakage when an integration's internals change.
 *
 * <p>Implementation rule: no provider-typed fields, only a class-presence flag; all third-party
 * references live in method bodies guarded by {@link #isAvailable()} and wrapped so a missing or
 * broken provider degrades to {@code null}/{@code false} instead of throwing.
 */
public interface Hook {

    /** Name of the backing plugin (e.g. "LuckPerms"). */
    String name();

    /** {@code true} if this hook can be used right now (backing plugin present and ready). */
    boolean isAvailable();
}
