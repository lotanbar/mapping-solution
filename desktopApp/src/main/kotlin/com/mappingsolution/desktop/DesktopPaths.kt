package com.mappingsolution.desktop

import java.io.File

/** Per-user app folders following each OS's conventions. */
internal object DesktopPaths {
    private const val APP_DIR = "MappingSolution"
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val home = File(System.getProperty("user.home"))

    /** Windows: %APPDATA%\MappingSolution. Linux: $XDG_DATA_HOME/MappingSolution (~/.local/share). */
    val dataDir: File by lazy {
        val base = if (isWindows) {
            System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
        } else {
            System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(home, ".local/share")
        }
        File(base, APP_DIR).also { it.mkdirs() }
    }

    /** Windows: %LOCALAPPDATA%\MappingSolution\cache. Linux: $XDG_CACHE_HOME/MappingSolution. */
    val cacheDir: File by lazy {
        val dir = if (isWindows) {
            File(System.getenv("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local"), "$APP_DIR/cache")
        } else {
            File(System.getenv("XDG_CACHE_HOME")?.let(::File) ?: File(home, ".cache"), APP_DIR)
        }
        dir.also { it.mkdirs() }
    }
}
