plugins {
    `java-library`
    `maven-publish`
}

description = "ProVouchers public API — services and events for dependent plugins"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.jetbrains.annotations)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-processing,-serial")
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        encoding = "UTF-8"
    }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            artifactId = "provouchers-api"
            from(components["java"])
            pom {
                name.set("ProVouchers API")
                description.set(project.description)
                url.set("https://github.com/alazso/provouchers")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "alazso"
            val isRelease = !project.version.toString().endsWith("-SNAPSHOT")
            url = uri(
                if (isRelease) "https://repo.alaz.so/releases"
                else "https://repo.alaz.so/snapshots"
            )
            credentials {
                username = System.getenv("ALAZSO_REPO_USER")
                password = System.getenv("ALAZSO_REPO_TOKEN")
            }
        }
    }
}
