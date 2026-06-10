package so.alaz.provouchers.platform;

/** Class-presence detection for soft (optional) integrations, used to degrade when a plugin is absent. */
public final class Classes {

    private Classes() {
    }

    /** {@code true} if the named class is loadable from the caller's classloader, without initializing it. */
    public static boolean present(String className, Class<?> caller) {
        try {
            Class.forName(className, false, caller.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
