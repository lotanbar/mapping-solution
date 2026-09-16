package com.mappingsolution.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.mappingsolution.ui.library.components.ColorPickerDialog
import com.mappingsolution.ui.library.components.parseHex

/**
 * A full-width outlined button showing the currently selected color swatch + hex text.
 * Tapping opens the color-picker dialog. Identical look to the one in GroupFormScreen.
 */
@Composable
fun ColorSelectorField(
    color: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ColorPickerDialog(
            initialHex = color,
            onConfirm = { onColorChange(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    val parsedColor = parseHex(color)
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .drawBehind { drawCircle(parsedColor) },
        )
        Spacer(Modifier.width(8.dp))
        Text(color)
        Spacer(Modifier.weight(1f))
        Text("Change", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}
