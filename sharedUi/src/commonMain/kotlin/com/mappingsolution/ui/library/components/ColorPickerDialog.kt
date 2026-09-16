package com.mappingsolution.ui.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ColorPickerDialog(
    initialHex: String,
    onConfirm: (hex: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialHex) { hexToHsv(initialHex) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var bri by remember { mutableFloatStateOf(initialHsv[2]) }

    val selectedColor = Color.hsv(hue, sat, bri)
    val initialColor = remember(initialHex) { parseHex(initialHex) }

    // Hue rainbow stops
    val hueGradient = remember {
        listOf(
            Color.Red,
            Color(1f, 0.5f, 0f),
            Color.Yellow,
            Color(0f, 1f, 0f),
            Color.Cyan,
            Color.Blue,
            Color(0.5f, 0f, 1f),
            Color.Magenta,
            Color.Red,
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Pick a color", style = MaterialTheme.typography.titleMedium)

                // ── SV gradient box ──────────────────────────────────────────
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun update(o: Offset) {
                                    sat = (o.x / size.width).coerceIn(0f, 1f)
                                    bri = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                }
                                update(down.position)
                                drag(down.id) { change -> update(change.position) }
                            }
                        },
                ) {
                    // White → full hue (horizontal)
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                    // Transparent → Black (vertical overlay)
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    // Crosshair indicator
                    val cx = sat * size.width
                    val cy = (1f - bri) * size.height
                    drawCircle(Color.White, 12f, Offset(cx, cy), style = Stroke(width = 3f))
                    drawCircle(Color.Black.copy(alpha = 0.4f), 12f, Offset(cx, cy), style = Stroke(width = 1f))
                }

                // ── Hue slider ───────────────────────────────────────────────
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun update(o: Offset) {
                                    hue = (o.x / size.width * 360f).coerceIn(0f, 360f)
                                }
                                update(down.position)
                                drag(down.id) { change -> update(change.position) }
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient(hueGradient))
                    val thumbX = hue / 360f * size.width
                    drawCircle(Color.White, 12f, Offset(thumbX, size.height / 2f), style = Stroke(width = 3f))
                    drawCircle(Color.Black.copy(alpha = 0.3f), 12f, Offset(thumbX, size.height / 2f), style = Stroke(width = 1f))
                }

                // ── Before / After preview ───────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Before",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .drawBehind { drawCircle(initialColor) }
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .drawBehind { drawCircle(selectedColor) }
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(
                        "After",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        hsvToHex(hue, sat, bri),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                // ── Buttons ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(hsvToHex(hue, sat, bri)) }) { Text("Select") }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

fun parseHex(hex: String): Color =
    parseArgb(hex)?.let { Color(it) } ?: Color(0xFF2196F3)

internal fun hexToHsv(hex: String): FloatArray {
    val argb = parseArgb(hex) ?: return floatArrayOf(210f, 0.86f, 0.95f)
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}

internal fun hsvToHex(hue: Float, sat: Float, bri: Float): String {
    val color = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), bri.coerceIn(0f, 1f))
    fun channel(value: Float) = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
    return "#FF%02X%02X%02X".format(channel(color.red), channel(color.green), channel(color.blue))
}

/** Parses `#RRGGBB` or `#AARRGGBB` into an ARGB int, or null when malformed. */
private fun parseArgb(hex: String): Int? {
    val digits = hex.removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> (0xFF000000 or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}
