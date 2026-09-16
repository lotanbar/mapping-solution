import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.0"
}

val localProperties = Properties().apply {
    val file = rootProject.file("../local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapTilerKey: String = localProperties.getProperty("MAPTILER_API_KEY", "uibCuK5qN9WReQo07MMt")

kotlin {
    jvmToolchain(25)
}

val maplibreCompose = "0.17.0"
val os = System.getProperty("os.name").lowercase()
val runtimeArtifact = when {
    os.contains("win") -> "maplibre-compose-runtime-vulkan-windows-x64"
    os.contains("linux") -> "maplibre-compose-runtime-vulkan-linux-x64"
    else -> error("Unsupported OS for spike: $os")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.maplibre.compose:maplibre-compose:$maplibreCompose")
    runtimeOnly("org.maplibre.compose:$runtimeArtifact:$maplibreCompose")
}

val generateConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/config")
    inputs.property("key", mapTilerKey)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/mappingsolution/desktop/Config.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.mappingsolution.desktop\n\ninternal object Config {\n" +
                "    const val MAPTILER_API_KEY = \"$mapTilerKey\"\n}\n"
        )
    }
}
sourceSets.main { kotlin.srcDir(generateConfig) }

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
            packageVersion = "1.0.0"
        }
    }
}
