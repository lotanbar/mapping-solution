package com.mappingsolution.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
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
    val automated = System.getenv("MS_AUTOMATION_PORT") != null
    val windowState = remember {
        // Automated runs use a fixed size so test coordinates are stable.
        if (automated) WindowState(size = DpSize(1280.dp, 800.dp))
        else WindowState(placement = WindowPlacement.Maximized, size = DpSize(1280.dp, 800.dp))
    }
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "MappingSolution") {
        LaunchedEffect(Unit) {
            if (automated) {
                // Stay behind the user's windows instead of grabbing focus.
                window.isAutoRequestFocus = false
                window.toBack()
            }
            DevAutomation.startIfEnabled(window)
        }
        ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DesktopApp(container)
            }
        }
    }
}
