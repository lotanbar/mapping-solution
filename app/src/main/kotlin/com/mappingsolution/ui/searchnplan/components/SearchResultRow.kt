package com.mappingsolution.ui.searchnplan.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappingsolution.data.model.SearchResult
import com.mappingsolution.ui.common.IconCatalog

@Composable
fun SearchResultRow(
    result: SearchResult,
    onNavigate: () -> Unit,
    onAddToPlan: (() -> Unit)? = null,
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (badgeText, badgeColor) = when (result) {
        is SearchResult.PersonalPoi -> "Personal" to Color(0xFF4CAF50)
        is SearchResult.ImportedPoi -> "Imported" to Color(0xFF2196F3)
        is SearchResult.OsmPoi -> "OSM" to Color(0xFFFF9800)
        is SearchResult.GooglePlace -> "Google" to Color(0xFF9C27B0)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (onOpenDetail != null) Modifier.clickable { onOpenDetail() } else Modifier)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(badgeColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )

            Spacer(Modifier.width(8.dp))

            Icon(
                painter = painterResource(IconCatalog.iconRes(result.poi.iconKey)),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = result.poi.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = { onAddToPlan?.invoke() },
            enabled = onAddToPlan != null,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to plan",
                tint = if (onAddToPlan != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            )
        }

        IconButton(onClick = onNavigate) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
            )
        }
    }
}
