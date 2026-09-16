package com.mappingsolution.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    surface = Surface,
    background = Background,
    onSurface = OnSurface
)

@Composable
fun MappingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
