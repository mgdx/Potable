plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Pure business logic: models, Hub'Eau JSON parsing, grouping by sample,
// UDI selection, commune search. No Android dependency, hence testable on the
// JVM without an emulator (SPEC §8, §12).
kotlin {
    // JDK 21 runs the compiler, because that is what F-Droid's build image
    // carries and it builds with toolchain auto-provisioning turned off. The
    // bytecode targets Java 11, like the application, for API 26.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// The communes referential as actually embedded in the application: the test
// parses and searches it rather than a sample written for the occasion, which
// proves that the generator and the reader agree (SPEC §4.2, §12).
tasks.withType<Test>().configureEach {
    systemProperty(
        "potable.communesFile",
        rootProject.file("data/src/main/assets/communes.tsv").absolutePath,
    )
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
