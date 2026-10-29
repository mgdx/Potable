plugins {
    // AGP 9 carries Kotlin support: applying "org.jetbrains.kotlin.android"
    // on top of it is an error.
    alias(libs.plugins.android.library)
    // Room's annotation processor runs through KSP, never kapt.
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

// Data sources: Hub'Eau client (OkHttp), embedded commune file, Room cache,
// DataStore, and later WorkManager (SPEC §7, §8).

// Room writes the schema of every database version here, and the directory
// is committed: without it no future migration can be checked.
ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

android {
    namespace = "io.github.mgdx.potable.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The exported schemas, as assets of the JVM tests: MigrationTestHelper
    // reads them there to build each old version and check every migration.
    sourceSets {
        getByName("test") {
            assets.directories.add(layout.projectDirectory.dir("schemas").asFile.path)
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the module's merged manifest and resources to
            // build an application context for the Room tests.
            isIncludeAndroidResources = true
        }
    }

    lint {
        // No warning tolerated (SPEC §12).
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

// Room-generated sources do not follow the project's style.
ktlint {
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

dependencies {
    // All three appear in this module's public signatures (models,
    // OkHttpClient, PotableDatabase built by AppContainer).
    api(project(":core"))
    api(libs.okhttp)
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    // Room needs SQLite: on the JVM, Robolectric provides it (in memory).
    testImplementation(libs.robolectric)
    // MigrationTestHelper: checks the migrations against the exported schemas.
    testImplementation(libs.room.testing)
}
