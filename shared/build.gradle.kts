plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Platform-neutral data layer shared by the Android app and the desktop app.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    api(libs.okhttp)
    api(libs.maplibre.geojson)
    implementation(libs.jsoup)
    // Android ships org.json in the platform; the desktop app adds it at runtime.
    compileOnly(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
