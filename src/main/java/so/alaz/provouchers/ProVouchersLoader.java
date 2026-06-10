package so.alaz.provouchers;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Loads ProVouchers' runtime libraries (the connection pool and JDBC drivers) through Paper's
 * library loader. Paper downloads them from Maven Central on first start and caches them, so the
 * server owner installs nothing extra. Only top-level artifacts are listed; transitive dependencies
 * resolve automatically.
 *
 * <p><strong>Coordinates must stay in sync with {@code gradle/libs.versions.toml}.</strong> The
 * MariaDB driver also serves {@code jdbc:mysql://} URLs, so no separate MySQL driver is needed.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ProVouchersLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        // Paper forbids resolving directly against Maven Central; use its bundled mirror.
        resolver.addRepository(new RemoteRepository.Builder(
            "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        for (String coordinates : new String[]{
            "com.zaxxer:HikariCP:7.0.2",
            "org.xerial:sqlite-jdbc:3.53.2.0",
            "org.mariadb.jdbc:mariadb-java-client:3.5.8",
            "org.postgresql:postgresql:42.7.7",
        }) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }

        classpath.addLibrary(resolver);
    }
}
