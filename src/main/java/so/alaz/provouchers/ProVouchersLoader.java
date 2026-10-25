package so.alaz.provouchers;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Loads ProVouchers' runtime libraries through Paper's library loader: the HikariCP connection pool and
 * the SQLite, MySQL (Connector/J), MariaDB, and PostgreSQL JDBC drivers. Paper downloads them on first
 * start and caches them. Only top-level artifacts are listed; transitive dependencies resolve
 * automatically.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ProVouchersLoader implements PluginLoader {

    /**
     * The top-level runtime libraries, generated from {@code gradle/libs.versions.toml} at build time, so the
     * versions Paper downloads are the versions the plugin was compiled against.
     */
    public static final List<String> LIBRARIES = RuntimeLibraries.COORDINATES;

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        // Paper forbids resolving directly against Maven Central, so this uses its mirror.
        resolver.addRepository(new RemoteRepository.Builder(
            "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinates : LIBRARIES) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }
        classpath.addLibrary(resolver);
    }
}
