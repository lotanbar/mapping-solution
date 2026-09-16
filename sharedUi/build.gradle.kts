plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Compose UI shared by the Android and desktop apps.
kotlin {
    android {
        namespace = "com.mappingsolution.ui.shared"
        compileSdk = 37
        minSdk = 26
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
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
