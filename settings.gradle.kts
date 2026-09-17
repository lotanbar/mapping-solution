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
plugins {
    // Downloads the JDK 25 the desktop map runtime needs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mapping-solution"
// `-PdesktopOnly` skips the Android app so desktop packages build without an Android SDK.
if (!providers.gradleProperty("desktopOnly").isPresent) include(":app")
include(":shared")
include(":sharedUi")
include(":desktopApp")
