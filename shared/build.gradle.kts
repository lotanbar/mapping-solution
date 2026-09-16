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
    // Same for the XmlPull API (desktop runtime: kxml2).
    compileOnly(libs.xmlpull)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kxml2)
}
