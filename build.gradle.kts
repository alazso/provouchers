plugins {
    java
    jacoco
    alias(libs.plugins.shadow)
}

description = "Feature-rich voucher plugin for Paper and Folia."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // The public API module, bundled into the plugin jar (consumers compile against
    // the published provouchers-api; the plugin provides it at runtime).
    implementation(project(":provouchers-api"))

    compileOnly(libs.paper.api)
    compileOnly(libs.jetbrains.annotations)

    // Soft integrations resolved by class presence at runtime; their Adventure deps come
    // from Paper, so MiniPlaceholders is pulled non-transitively.
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholders.api) { isTransitive = false }

    // Connection pool: compiled against, loaded at runtime by ProVouchersLoader (with the JDBC
    // drivers) so it is not shaded into the jar.
    compileOnly(libs.hikari)

    // Metrics: shaded + relocated into the jar (see shadowJar relocations below).
    implementation(libs.bstats.bukkit)
    implementation(libs.faststats.bukkit)

    // Integration APIs: provided at runtime by the respective server plugins (soft-depend),
    // guarded by class-presence checks. Never bundled, never runtime-loaded. Declared
    // non-transitively where their dependency trees are heavy or carry conflicting constraints.
    compileOnly(libs.luckperms.api)
    compileOnly(libs.vault.api) { isTransitive = false }
    compileOnly(libs.itemsadder.api) { isTransitive = false }
    compileOnly(libs.oraxen) { isTransitive = false }
    compileOnly(libs.nexo) { isTransitive = false }
    compileOnly(libs.headdatabase.api) { isTransitive = false }
    compileOnly(libs.worldguard.bukkit) { isTransitive = false }
    compileOnly(libs.worldguard.core) { isTransitive = false }
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Storage matrix tests run migrations and a CRUD round-trip against real databases. The pool
    // and JDBC drivers (loaded at runtime by ProVouchersLoader in production) go on the test
    // classpath; Testcontainers supplies ephemeral servers and is skipped when Docker is absent.
    testImplementation(libs.hikari)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.sqlite.jdbc)
    testRuntimeOnly(libs.mysql.connector)
    testRuntimeOnly(libs.mariadb.jdbc)
    testRuntimeOnly(libs.postgresql.jdbc)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -classfile: the Kotlin-compiled integration APIs (Nexo) are declared non-transitively,
    // so their kotlin.* annotation types are absent at compile time by design.
    options.compilerArgs.add("-Xlint:all,-processing,-serial,-classfile")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Runtime-only classes that cannot be unit tested without a live server are excluded
// from the coverage bar; the gate guards the unit-testable core.
val coverageExclusions = listOf(
    "so/alaz/provouchers/ProVouchersPlugin.class",
    "**/*Bootstrap*.class",
    "**/*Loader*.class",
    "**/*Listener*.class",
    "**/command/**",
    "**/hook/**",
    "**/storage/**",
    "**/antidupe/**",
    "**/redeem/**",
    "**/metrics/**",
    "**/cooldown/**",
    "**/service/**",
    "**/gui/**",
    "**/give/**",
    "**/platform/**",
    "**/condition/**",
    "**/voucher/VoucherItemFactory.class",
    "**/voucher/ItemResolver.class",
    "**/config/ConfigManager.class",
    "**/stash/StashService.class",
)

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) { exclude(coverageExclusions) }
        },
    )
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        encoding = "UTF-8"
    }
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

// The shadow jar is the deliverable: the plugin classes plus the bundled
// provouchers-api module (no relocation, so the api stays at so.alaz.provouchers.api).
tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    relocate("org.bstats", "${project.group}.libs.bstats")
    relocate("dev.faststats", "${project.group}.libs.faststats")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}
