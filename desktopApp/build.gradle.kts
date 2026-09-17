import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

kotlin {
    // maplibre-compose's desktop runtime uses the Java 25 foreign-function API.
    jvmToolchain(25)
}

val os = System.getProperty("os.name").lowercase()
val mapRuntime = when {
    os.contains("win") -> "maplibre-compose-runtime-vulkan-windows-x64"
    os.contains("linux") -> "maplibre-compose-runtime-vulkan-linux-x64"
    else -> error("Desktop builds support Windows and Linux; found $os")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sharedUi"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.maplibre.compose)
    runtimeOnly("org.maplibre.compose:$mapRuntime:${libs.versions.maplibreCompose.get()}")
    implementation(libs.kotlinx.coroutines.swing)
    // Android provides these in the platform; the desktop JVM needs real implementations.
    implementation(libs.org.json)
    runtimeOnly(libs.kxml2)
    // Reads MBTiles metadata on import.
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}

val generateBuildConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildConfig")
    val mapTiler = localProperties.getProperty("MAPTILER_API_KEY", "uibCuK5qN9WReQo07MMt")
    val mapillary = localProperties.getProperty("MAPILLARY_ACCESS_TOKEN", "")
    inputs.property("mapTiler", mapTiler)
    inputs.property("mapillary", mapillary)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/mappingsolution/desktop/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.mappingsolution.desktop
            |
            |internal object BuildConfig {
            |    const val MAPTILER_API_KEY = "$mapTiler"
            |    const val MAPILLARY_ACCESS_TOKEN = "$mapillary"
            |}
            |""".trimMargin()
        )
    }
}
sourceSets.main { kotlin.srcDir(generateBuildConfig) }

compose.desktop {
    application {
        mainClass = "com.mappingsolution.desktop.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "MappingSolution"
            packageVersion = "1.4.0"
            modules("java.prefs", "java.net.http", "java.sql", "jdk.crypto.ec", "jdk.httpserver")
        }
    }
}
