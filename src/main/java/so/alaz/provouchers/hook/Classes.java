package so.alaz.provouchers.hook;

/** Class-presence detection shared by the hook implementations. */
final class Classes {

    private Classes() {
    }

    /** {@code true} if the named class is loadable from the caller's classloader. */
    static boolean present(String className, Class<?> caller) {
        try {
            Class.forName(className, false, caller.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
