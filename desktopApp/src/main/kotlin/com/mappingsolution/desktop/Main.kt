package com.mappingsolution.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

fun main() = application {
    val container = remember { AppContainer() }
    val windowState = remember { WindowState(placement = WindowPlacement.Maximized, size = DpSize(1280.dp, 800.dp)) }
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "MappingSolution") {
        ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MapScreen(container)
            }
        }
    }
}
