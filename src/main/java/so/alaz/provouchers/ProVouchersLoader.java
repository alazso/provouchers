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
     * The top-level runtime libraries. Each version must match {@code gradle/libs.versions.toml}, which is
     * not available at runtime; {@code ProVouchersLoaderTest} fails the build when they differ.
     */
    public static final List<String> LIBRARIES = List.of(
        "com.zaxxer:HikariCP:7.1.0",
        "org.xerial:sqlite-jdbc:3.53.4.0",
        "com.mysql:mysql-connector-j:26.10.0",
        "org.mariadb.jdbc:mariadb-java-client:3.5.11",
        "org.postgresql:postgresql:42.7.14"
    );

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
