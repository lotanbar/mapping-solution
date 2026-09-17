import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.mappingsolution"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mappingsolution"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.3.0-debug"

        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${localProperties.getProperty("MAPTILER_API_KEY", "uibCuK5qN9WReQo07MMt")}\""
        )
        buildConfigField(
            "String",
            "MAPILLARY_ACCESS_TOKEN",
            "\"${localProperties.getProperty("MAPILLARY_ACCESS_TOKEN", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sharedUi"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    // MapLibre
    implementation(libs.maplibre.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // OkHttp
    implementation(libs.okhttp)

    // WorkManager + Hilt integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Drag-to-reorder for destination list
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
