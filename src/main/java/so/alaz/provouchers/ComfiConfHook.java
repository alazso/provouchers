package so.alaz.provouchers;

/**
 * ProVouchers' view of its optional ComfiConf integration. Declared without any {@code com.comficonf.api} type, so
 * classes loaded at startup can hold it while the ComfiConf bridge is absent.
 */
public interface ComfiConfHook {

    /** Id of the registered configuration resource. */
    String resourceId();

    /** Whether the resource is still registered with the bridge. */
    boolean isRegistered();

    /** Tells the bridge that config.yml changed outside ComfiConf, so open editors load the newer values. */
    void notifyChanged();

    /** Unregisters the resource. */
    void close();
}
