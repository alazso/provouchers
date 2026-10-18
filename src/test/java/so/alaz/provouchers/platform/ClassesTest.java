package so.alaz.provouchers.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassesTest {

    @Test
    void findsALoadableClass() {
        assertThat(Classes.present("java.lang.String", Classes.class)).isTrue();
    }

    @Test
    void reportsAnAbsentClass() {
        assertThat(Classes.present("no.such.plugin.Api", Classes.class)).isFalse();
    }

    @Test
    void reportsAbsentWhenAnotherPluginsClassloaderThrows() {
        // As the server does when a plugin failed to enable and its jar was closed.
        ClassLoader closed = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                throw new IllegalStateException("The plugin classloader for Other has thrown a zip file error");
            }
        };
        assertThat(Classes.present("com.sk89q.worldguard.WorldGuard", closed)).isFalse();
    }

    @Test
    void reportsAbsentWhenTheLookupFailsToLink() {
        ClassLoader broken = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                throw new NoClassDefFoundError(name);
            }
        };
        assertThat(Classes.present("dev.lone.itemsadder.api.CustomStack", broken)).isFalse();
    }
}
