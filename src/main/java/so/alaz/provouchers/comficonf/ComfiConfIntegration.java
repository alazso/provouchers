package so.alaz.provouchers.comficonf;

import com.comficonf.api.CallbackThread;
import com.comficonf.api.ComfiConfApi;
import com.comficonf.api.ConfigResource;
import com.comficonf.api.ResourceRegistration;
import org.bukkit.plugin.java.JavaPlugin;
import so.alaz.provouchers.ComfiConfHook;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Registers config.yml with ComfiConf. Loaded only after the ComfiConf bridge was found enabled. */
public final class ComfiConfIntegration implements ComfiConfHook {

    /** Resource id of config.yml. The editor and the change history refer to it, so it never changes. */
    public static final String RESOURCE_ID = "provouchers:config";

    private final ResourceRegistration registration;

    private ComfiConfIntegration(ResourceRegistration registration) {
        this.registration = registration;
    }

    /**
     * Registers config.yml with the running ComfiConf bridge. Call from {@code onEnable}, after config.yml was loaded.
     *
     * @param plugin the owning plugin
     * @param configFile the config.yml file in the data folder
     * @param backupDirectory where the previous file is backed up before every save from ComfiConf
     * @param reload re-reads config.yml into the copy {@code getConfig()} returns; called on the server main thread
     *     after a save, returns {@code true} when the file loaded, and never writes the file
     * @return the handle the plugin keeps until {@code onDisable}
     * @throws IllegalStateException if the bridge or the plugin is not enabled
     * @throws IllegalArgumentException if the resource id is already registered
     */
    public static ComfiConfHook register(JavaPlugin plugin, Path configFile, Path backupDirectory,
                                         Callable<Boolean> reload) {
        ConfigResource resource = ConfigResource.builder(RESOURCE_ID)
            .displayName("ProVouchers settings")
            .description("config.yml of ProVouchers: command aliases, language, storage, anti-dupe, redeeming, "
                + "the Stash and metrics. Vouchers and codes stay in their own files.")
            .provider(new ConfigYmlProvider(configFile, backupDirectory, plugin.getLogger(), reload))
            .callbackThread(CallbackThread.WORKER)
            .build();
        return new ComfiConfIntegration(ComfiConfApi.get().register(plugin, resource));
    }

    @Override
    public String resourceId() {
        return RESOURCE_ID;
    }

    @Override
    public boolean isRegistered() {
        return registration.isActive();
    }

    @Override
    public void notifyChanged() {
        registration.notifyChanged();
    }

    @Override
    public void close() {
        registration.unregister();
    }
}
