pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// No `foojay-resolver-convention`, whatever the Android Studio template
// proposes: it resolves a missing Java toolchain by downloading a JDK, and
// F-Droid's scanner refuses a build able to fetch one. The JDK 21 the modules
// ask for is the one F-Droid's build image already carries.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Potable"
// :core depends on no Android API: the compiler refuses an Android import
// there, which keeps the business logic testable on the JVM (SPEC §8).
include(":core")
include(":data")
include(":app")
 