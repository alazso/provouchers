rootProject.name = "provouchers"

plugins {
    // Auto-provisions the Java 25 toolchain when not installed locally.
    // Declared with an explicit version: the version catalog is not available in settings scripts.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")                    { name = "papermc" }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")  { name = "placeholderapi" }
        maven("https://repo.faststats.dev/releases")                                 { name = "faststats" }
        maven("https://jitpack.io")                                                  { name = "jitpack" }
        maven("https://repo.oraxen.com/releases")                                    { name = "oraxen" }
        maven("https://repo.nexomc.com/releases")                                    { name = "nexo" }
        maven("https://maven.enginehub.org/repo/")                                   { name = "enginehub" }
        maven("https://repo.alaz.so/releases")  { name = "alazso" }
        maven("https://repo.alaz.so/snapshots") { name = "alazso-snapshots" }
    }
}

include(":provouchers-api")
