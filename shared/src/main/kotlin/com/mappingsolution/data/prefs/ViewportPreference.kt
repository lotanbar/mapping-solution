package com.mappingsolution.data.prefs

import com.mappingsolution.data.util.KeyValueStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewportPreference @Inject constructor(
    stores: KeyValueStore.Factory,
) {
    private val prefs = stores.open("viewport")

    fun save(lat: Double, lng: Double, zoom: Double, bearing: Double = 0.0, tilt: Double = 0.0) {
        prefs.putLongs(
            mapOf(
                "lat" to lat.toBits(),
                "lng" to lng.toBits(),
                "zoom" to zoom.toBits(),
                "bearing" to bearing.toBits(),
                "tilt" to tilt.toBits(),
            )
        )
    }

    fun load(): SavedCamera? {
        if (!prefs.contains("lat")) return null
        return SavedCamera(
            lat = Double.fromBits(prefs.getLong("lat", 0L)),
            lng = Double.fromBits(prefs.getLong("lng", 0L)),
            zoom = Double.fromBits(prefs.getLong("zoom", java.lang.Double.doubleToLongBits(12.0))),
            bearing = Double.fromBits(prefs.getLong("bearing", 0L)),
            tilt = Double.fromBits(prefs.getLong("tilt", 0L)),
        )
    }

    data class SavedCamera(
        val lat: Double,
        val lng: Double,
        val zoom: Double,
        val bearing: Double,
        val tilt: Double,
    )
}
