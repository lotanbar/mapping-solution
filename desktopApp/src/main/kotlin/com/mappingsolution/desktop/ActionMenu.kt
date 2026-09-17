package com.mappingsolution.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The desktop counterpart of Android's bottom action panel: a toggle button whose feature
 * buttons unfold above it. Recording is left out because desktop has no live GPS.
 */
@Composable
internal fun ActionMenu(
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onAddPoi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ActionButton(Icons.Default.AddLocation, "New POI at map center", onAddPoi)
                    ActionButton(Icons.Default.Search, "Search & Plan", onOpenSearch)
                    ActionButton(Icons.Default.Folder, "Library", onOpenLibrary)
                }
            }
            ActionButton(
                icon = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                label = if (expanded) "Collapse menu" else "Expand menu",
                onClick = { expanded = !expanded },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.85f)) {
                Text(label, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.CenterEnd,
            alignment = Alignment.CenterEnd,
            offset = DpOffset(8.dp, 0.dp),
        ),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp), tint = Color.White)
        }
    }
}
