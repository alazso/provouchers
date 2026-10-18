package so.alaz.provouchers.platform;

import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/** Class-presence detection for soft (optional) integrations, used to degrade when a plugin is absent. */
public final class Classes {

    private static final Logger LOG = Logger.getLogger("ProVouchers");

    private Classes() {
    }

    /** {@code true} if the named class is loadable from the caller's classloader, without initializing it. */
    public static boolean present(String className, Class<?> caller) {
        return present(className, caller.getClassLoader());
    }

    /**
     * {@code true} if the named class is loadable from {@code loader}, without initializing it.
     *
     * <p>A lookup can fail for reasons other than the class being absent: the server searches every
     * plugin's classloader, so a plugin that failed to enable and had its jar closed makes the lookup
     * throw. The class then counts as absent, with a warning, rather than failing the caller.
     */
    static boolean present(String className, @Nullable ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        } catch (LinkageError | RuntimeException ex) {
            LOG.warning("Could not look up " + className + ", so the integration using it stays off. "
                + "Another plugin on this server is likely broken: " + ex);
            return false;
        }
    }
}
