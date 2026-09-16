package com.mappingsolution.ui.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PRESET_COLORS = listOf(
    "#FFF44336", // Red
    "#FFFF5722", // Deep Orange
    "#FFFF9800", // Orange
    "#FFFFC107", // Amber
    "#FFFFEB3B", // Yellow
    "#FF8BC34A", // Light Green
    "#FF4CAF50", // Green
    "#FF009688", // Teal
    "#FF2196F3", // Blue
    "#FF03A9F4", // Light Blue
    "#FF9C27B0", // Purple
    "#FFE91E63", // Pink
    "#FF795548", // Brown
    "#FF607D8B", // Blue Grey
    "#FF9E9E9E", // Grey
    "#FF212121", // Near Black
)

@Composable
fun ColorPickerRow(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        modifier = modifier.height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PRESET_COLORS) { hex ->
            val color = parseColor(hex)
            val isSelected = hex.equals(selectedColor, ignoreCase = true)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(color) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .clickable { onColorSelected(hex) },
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        val cleaned = hex.trimStart('#')
        val long = cleaned.toLong(16)
        val a = if (cleaned.length == 8) ((long shr 24) and 0xFF) / 255f else 1f
        val r = ((long shr 16) and 0xFF) / 255f
        val g = ((long shr 8) and 0xFF) / 255f
        val b = (long and 0xFF) / 255f
        Color(r, g, b, a)
    } catch (_: Exception) {
        Color.Gray
    }
}
