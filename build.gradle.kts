plugins {
    java
    jacoco
}

description = "Feature-rich voucher plugin for Paper and Folia, built on Strata."

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

    // Strata is a hard runtime dependency, present on the server and loaded first.
    // We compile against its API only; its runtime libraries (Kotlin stdlib, JDBC
    // drivers, connection pool) are provided by the installed Strata plugin.
    compileOnly(libs.strata.api)

    testImplementation(libs.paper.api)
    testImplementation(libs.strata.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-processing,-serial")
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
