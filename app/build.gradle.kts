import java.util.Properties

plugins {
    // AGP 9 carries Kotlin support: applying "org.jetbrains.kotlin.android"
    // on top of it is an error.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Type-safe navigation routes (ui/navigation/Destinations.kt).
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.mgdx.potable"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.mgdx.potable"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.4.0"
    }

    // The NDK that strips the native libraries the dependencies ship
    // (DataStore's shared counter, androidx.graphics.path), named rather than
    // left to whichever one AGP finds: two NDKs strip to two different files,
    // and the APK is no longer reproducible. The F-Droid recipe names the same
    // version in its `ndk` field (docs/release.md).
    ndkVersion = "28.2.13676358"

    // Release signing (docs/release.md). The key and its passwords live in
    // `keystore.properties` at the root, which Git ignores and which usually
    // does not exist: then the release APK is simply left unsigned, and no
    // key is ever made up here.
    val signingProperties = rootProject.file("keystore.properties")
    signingConfigs {
        if (signingProperties.exists()) {
            create("release") {
                val values = Properties()
                signingProperties.inputStream().use { values.load(it) }
                storeFile = rootProject.file(values.getProperty("storeFile"))
                storePassword = values.getProperty("storePassword")
                keyAlias = values.getProperty("keyAlias")
                keyPassword = values.getProperty("keyPassword")
                // v3 carries the certificate's lineage: the only way to move
                // to a new key one day without breaking every update.
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 and resource shrinking (SPEC §2: APK under 6 MB). AGP 9
            // applies proguard-android-optimize.txt by default, plus the
            // rules of src/main/keepRules/ and those every library ships;
            // see that file for why it adds almost nothing.
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingProperties.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // AGP writes the commit hash into the APK when it finds a Git
            // checkout, and an error message when it does not (a worktree, a
            // source tarball): the same sources would then give two APKs.
            vcsInfo {
                include = false
            }
        }
    }

    // The dependency block AGP signs into the APK is opaque to anyone but
    // Google and not reproducible; F-Droid asks for it to be left out.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // English and French only (SPEC §11.1): the libraries' translations into
    // other languages (Material 3, Compose UI...) are left out of the APK, so
    // that no screen shows a third language. Keep in step with
    // res/xml/locales_config.xml.
    androidResources {
        localeFilters += listOf("en", "fr")
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME for the About screen.
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric builds its application context from the merged
            // manifest and resources.
            isIncludeAndroidResources = true
        }
    }

    lint {
        // No warning tolerated (SPEC §12).
        warningsAsErrors = true
        abortOnError = true
        // Translations (SPEC §11): a missing or extra string, a missing or
        // useless plural quantity, a physical direction in a resource fail
        // the build whatever their default severity; so does a values-xx
        // folder missing from res/xml/locales_config.xml.
        error +=
            setOf(
                "MissingTranslation",
                "ExtraTranslation",
                "MissingQuantity",
                "UnusedQuantity",
                "RtlHardcoded",
                "RtlSymmetry",
                "UnusedTranslation",
            )
    }
}

kotlin {
    // JDK 21 runs the compiler (F-Droid's build image); bytecode stays at
    // Java 11 for API 26.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // The periodic alert check (SPEC §5 F7).
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // An Android Context for the tests of intents and of the WorkManager
    // requests; work-testing runs WorkManager synchronously in them.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
