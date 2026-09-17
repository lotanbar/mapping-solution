package com.mappingsolution.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Height of [ActionBar]; the side panel starts below it. */
internal val ACTION_BAR_HEIGHT = 56.dp

internal enum class PanelSection { Library, Search, NewPoi }

/**
 * The desktop counterpart of Android's bottom action panel, pinned to the top left.
 * Recording is left out because desktop has no live GPS.
 */
@Composable
internal fun ActionBar(
    active: PanelSection?,
    onClick: (PanelSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        // Flush with the window corner; only the inner corner is rounded.
        shape = RoundedCornerShape(bottomEnd = 28.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Row {
            ActionButton(Icons.Default.AddLocation, "New POI at map center", active == PanelSection.NewPoi) {
                onClick(PanelSection.NewPoi)
            }
            ActionButton(Icons.Default.Folder, "Library", active == PanelSection.Library) {
                onClick(PanelSection.Library)
            }
            ActionButton(Icons.Default.Search, "Search & Plan", active == PanelSection.Search) {
                onClick(PanelSection.Search)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.85f)) {
                Text(label, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            alignment = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp), tint = Color.White)
        }
    }
}
