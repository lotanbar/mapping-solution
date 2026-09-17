package com.mappingsolution.desktop

import com.mappingsolution.data.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** One-shot current location from the operating system's location service, where there is one. */
internal object DesktopLocation {
    private const val TAG = "DesktopLocation"
    private const val TIMEOUT_SECONDS = 15L

    /** Returns `lat to lng`, or null when the OS has no location service or it gave no fix. */
    suspend fun current(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val os = System.getProperty("os.name").lowercase()
        val output = when {
            os.contains("win") -> run(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_SCRIPT,
            )
            // GeoClue's demo client ships with the geoclue package on most distributions.
            else -> GEOCLUE_CLIENTS.firstOrNull { File(it).canExecute() }?.let { run(it, "-t", "10") }
        }
        parse(output).also { AppLog.d(TAG, if (it != null) "Location fix received" else "No location fix") }
    }

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        process.inputStream.bufferedReader().readText()
    }.onFailure { AppLog.w(TAG, "Location lookup failed", it) }.getOrNull()

    /** Reads `Latitude: 12.3°` / `Longitude: 45.6°` lines, the format both scripts print. */
    private fun parse(output: String?): Pair<Double, Double>? {
        output ?: return null
        fun value(label: String) = Regex("""$label:\s*(-?\d+(?:[.,]\d+)?)""")
            .find(output)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val lat = value("Latitude") ?: return null
        val lng = value("Longitude") ?: return null
        return (lat to lng).takeIf { lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0) }
    }

    private val GEOCLUE_CLIENTS = listOf(
        "/usr/libexec/geoclue-2.0/demos/where-am-i",
        "/usr/lib/geoclue-2.0/demos/where-am-i",
    )

    private val WINDOWS_SCRIPT = """
        Add-Type -AssemblyName System.Device
        ${'$'}w = New-Object System.Device.Location.GeoCoordinateWatcher([System.Device.Location.GeoPositionAccuracy]::High)
        [void]${'$'}w.TryStart(${'$'}false, [TimeSpan]::FromSeconds(10))
        ${'$'}i = 0
        while (${'$'}w.Position.Location.IsUnknown -and ${'$'}i -lt 50) { Start-Sleep -Milliseconds 200; ${'$'}i++ }
        ${'$'}l = ${'$'}w.Position.Location
        if (-not ${'$'}l.IsUnknown) {
            [Console]::WriteLine('Latitude: ' + ${'$'}l.Latitude.ToString([Globalization.CultureInfo]::InvariantCulture))
            [Console]::WriteLine('Longitude: ' + ${'$'}l.Longitude.ToString([Globalization.CultureInfo]::InvariantCulture))
        }
        ${'$'}w.Stop()
    """.trimIndent()
}
