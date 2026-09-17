import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// `-PdesktopOnly` builds without the Android target, e.g. on Linux hosts without an Android SDK.
val desktopOnly = providers.gradleProperty("desktopOnly").isPresent
if (!desktopOnly) apply(plugin = libs.plugins.android.kmp.library.get().pluginId)

// Compose UI shared by the Android and desktop apps.
kotlin {
    if (!desktopOnly) {
        extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            namespace = "com.mappingsolution.ui.shared"
            compileSdk = 37
            minSdk = 26
            // Required for Compose resources (icons) to be packaged into the APK.
            androidResources.enable = true
        }
    }
    jvm("desktop")
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            api(compose.components.resources)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.backhandler)
            api(libs.coil3.compose)
            implementation(libs.reorderable)
            implementation(libs.coil3.network.okhttp)
            implementation(libs.kotlinx.coroutines.core)
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.mappingsolution.ui.resources"
}
