package com.mappingsolution.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.mappingsolution.ui.common.resolvedTextAlign
import com.mappingsolution.ui.common.resolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.DestinationSource
import com.mappingsolution.data.model.MediaUtils
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.places.WikimediaContent
import com.mappingsolution.ui.common.TopToast
import com.mappingsolution.ui.common.GroupPickerField
import com.mappingsolution.ui.poi.NoMediaPlaceholder
import com.mappingsolution.ui.poi.PoiGroupSourceIcon
import com.mappingsolution.ui.poi.PoiInfoBlock
import com.mappingsolution.ui.poi.PoiMediaPager
import com.mappingsolution.ui.searchnplan.NavigationIntentHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditPoi: (poiId: String) -> Unit,
    onNavigateToEditRoute: (routeId: String) -> Unit,
    onOpenMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit,
    fromSearch: Boolean = false,
    onNavigate: ((lat: Double, lng: Double) -> Unit)? = null,
    onAddToPlan: ((PlanDestination) -> Unit)? = null,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val bookmark by viewModel.bookmark.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val context = LocalContext.current
    var topToastMessage by remember { mutableStateOf<String?>(null) }

    val isRoute = state.item is DetailItem.RouteDetail

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = if (isRoute) ({
            TopAppBar(
                title = { Text((state.item as? DetailItem.RouteDetail)?.route?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }) else ({})
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        when (val item = state.item) {
            null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Not found", style = MaterialTheme.typography.bodyLarge) }

            is DetailItem.PoiDetail -> PoiDetailContent(
                item = item,
                modifier = Modifier.padding(padding),
                isReadOnly = item.isReadOnly,
                fromSearch = fromSearch,
                onNavigateBack = onNavigateBack,
                onNavigateToEdit = onNavigateToEditPoi,
                onOpenMediaPreview = onOpenMediaPreview,
                onDeleteClick = { viewModel.deletePoi(it) },
                onRemoveMedia = { viewModel.removePoiMediaItem(it) },
                onNavigate = onNavigate,
                onAddToPlan = onAddToPlan,
                onShowMessage = { topToastMessage = it },
                bookmarkGroupId = bookmark?.groupId,
                groups = groups,
                isStarred = bookmark != null,
                isSaving = isSaving,
                onStarClick = { viewModel.toggleStar { topToastMessage = it } },
                onStarGroupSelected = { viewModel.setStarGroup(it) { message -> topToastMessage = message } },
                context = context,
            )

            is DetailItem.RouteDetail -> RouteDetailContent(
                item = item,
                modifier = Modifier.padding(padding),
                onNavigateBack = onNavigateBack,
                onNavigateToEdit = onNavigateToEditRoute,
                onDeleteClick = { viewModel.deleteRoute(it) },
                onShowMessage = { topToastMessage = it },
                context = context,
            )
        }
    }
        TopToast(message = topToastMessage, onDismiss = { topToastMessage = null })
    }
}

@Composable
private fun PoiDetailContent(
    item: DetailItem.PoiDetail,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    fromSearch: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (poiId: String) -> Unit,
    onOpenMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit,
    onDeleteClick: (onDeleted: () -> Unit) -> Unit,
    onRemoveMedia: (index: Int) -> Unit,
    onNavigate: ((lat: Double, lng: Double) -> Unit)? = null,
    onAddToPlan: ((PlanDestination) -> Unit)? = null,
    onShowMessage: (String) -> Unit = {},
    bookmarkGroupId: String? = null,
    groups: List<com.mappingsolution.data.model.Group> = emptyList(),
    isStarred: Boolean = false,
    isSaving: Boolean = false,
    onStarClick: () -> Unit = {},
    onStarGroupSelected: (String?) -> Unit = {},
    context: android.content.Context,
) {
    val poi = item.poi
    var lastDeleteClickTime by remember { mutableStateOf(0L) }

    if (isReadOnly && !fromSearch) {
        ReadOnlyPoiFullLayout(
            item = item,
            modifier = modifier,
            onOpenMediaPreview = onOpenMediaPreview,
            bookmarkGroupId = bookmarkGroupId,
            groups = groups,
            isStarred = isStarred,
            isSaving = isSaving,
            onStarClick = onStarClick,
            onStarGroupSelected = onStarGroupSelected,
            context = context,
        )
        return
    }

    val galleryHeight = LocalConfiguration.current.screenHeightDp.dp * 0.55f
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (item.mediaPaths.isNotEmpty()) {
                val mediaItems = item.mediaPaths.mapIndexed { index, path ->
                    MediaUtils.createMediaItem(path, index)
                }
                PoiMediaPager(
                    mediaItems = mediaItems,
                    onItemClick = { index -> onOpenMediaPreview(poi.id, index, item.mediaPaths) },
                    modifier = Modifier.height(galleryHeight),
                )
            } else {
                NoMediaPlaceholder(modifier = Modifier.height(galleryHeight))
            }

            PoiInfoBlock(
                poi = poi,
                group = item.group,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 90.dp),
            )
            WikimediaAttribution(
                content = item.wikimediaContent,
                context = context,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (fromSearch) {
            SearchContextBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                showStar = isReadOnly,
                bookmarkGroupId = bookmarkGroupId,
                groups = groups,
                isStarred = isStarred,
                isSaving = isSaving,
                onStarClick = onStarClick,
                onStarGroupSelected = onStarGroupSelected,
                onNavigateClick = { onNavigate?.invoke(poi.lat, poi.lng) },
                onAddToPlanClick = {
                    val dest = PlanDestination(
                        sourceType = item.sourceType,
                        sourceId = poi.id,
                        name = poi.name,
                        lat = poi.lat,
                        lng = poi.lng,
                    )
                    onAddToPlan?.invoke(dest)
                },
            )
        } else {
            DetailBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                onEditClick = { onNavigateToEdit(poi.id) },
                editLabel = "Edit POI",
                deleteLabel = "Remove POI",
                isReadOnly = isReadOnly,
                onDeleteClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastDeleteClickTime < 2000) {
                        onDeleteClick {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                onNavigateBack()
                            }, 100)
                        }
                    } else {
                        lastDeleteClickTime = now
                        onShowMessage("Tap again quickly to remove POI")
                    }
                },
                onShowMessage = onShowMessage,
            )
        }
    }
}

@Composable
private fun RouteDetailContent(
    item: DetailItem.RouteDetail,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (routeId: String) -> Unit,
    onDeleteClick: (onDeleted: () -> Unit) -> Unit,
    onShowMessage: (String) -> Unit = {},
    context: android.content.Context,
) {
    val route = item.route
    var lastDeleteClickTime by remember { mutableStateOf(0L) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LabeledField("Name", route.name)

            if (!route.description.isNullOrBlank()) {
                LabeledField("Description", route.description)
            }

            ColorField(label = "Color", colorHex = route.color)

            LabeledField("Distance", formatDistance(route.distanceMeters))

            LabeledField("Duration", formatDuration(route.durationSec))

            LabeledField("Recorded", formatDate(route.startedAt))
        }

        DetailBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onEditClick = { onNavigateToEdit(route.id) },
            editLabel = "Edit Route",
            deleteLabel = "Remove Route",
            onDeleteClick = {
                val now = System.currentTimeMillis()
                if (now - lastDeleteClickTime < 2000) {
                    onDeleteClick {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            onNavigateBack()
                        }, 100)
                    }
                } else {
                    lastDeleteClickTime = now
                    onShowMessage("Tap again quickly to remove route")
                }
            },
        )
    }
}

@Composable
private fun SearchContextBottomBar(
    modifier: Modifier = Modifier,
    showStar: Boolean,
    bookmarkGroupId: String?,
    groups: List<com.mappingsolution.data.model.Group>,
    isStarred: Boolean,
    isSaving: Boolean,
    onStarClick: () -> Unit,
    onStarGroupSelected: (String?) -> Unit,
    onNavigateClick: () -> Unit,
    onAddToPlanClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showStar && isStarred) {
                GroupPickerField(
                    groups = groups,
                    selectedGroupId = bookmarkGroupId,
                    onGroupSelected = onStarGroupSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showStar) {
                    StarButton(
                        isStarred = isStarred,
                        isSaving = isSaving,
                        onClick = onStarClick,
                        modifier = Modifier.weight(1f).height(52.dp),
                    )
                }
                Button(
                    onClick = onNavigateClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Navigate")
                }
            }
            Button(
                onClick = onAddToPlanClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Add to Plan")
            }
        }
    }
}

@Composable
private fun StarButton(
    isStarred: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isSaving,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isStarred) "Remove star" else "Star POI",
            tint = if (isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ReadOnlyPoiFullLayout(
    item: DetailItem.PoiDetail,
    modifier: Modifier = Modifier,
    onOpenMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit,
    bookmarkGroupId: String?,
    groups: List<com.mappingsolution.data.model.Group>,
    isStarred: Boolean,
    isSaving: Boolean,
    onStarClick: () -> Unit,
    onStarGroupSelected: (String?) -> Unit,
    context: android.content.Context,
) {
    val poi = item.poi
    val group = item.group
    val mediaItems = item.mediaPaths.mapIndexed { index, path ->
        MediaUtils.createMediaItem(path, index)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (mediaItems.isNotEmpty()) {
            PoiMediaPager(
                mediaItems = mediaItems,
                onItemClick = { index -> onOpenMediaPreview(poi.id, index, item.mediaPaths) },
                modifier = Modifier.weight(1f),
            )
        } else {
            NoMediaPlaceholder(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = poi.name,
                style = MaterialTheme.typography.headlineMedium.copy(
                    textDirection = poi.name.resolvedTextDirection(),
                    textAlign = poi.name.resolvedTextAlign(),
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!poi.description.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 2.dp),
                ) {
                    Text(
                        text = poi.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = poi.description.resolvedTextDirection(),
                            textAlign = poi.description.resolvedTextAlign(),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            group?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    PoiGroupSourceIcon(
                        group = it,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (it.id == OSM_POI_GROUP_ID) "Open Street Map" else it.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            WikimediaAttribution(item.wikimediaContent, context)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isStarred) {
                    GroupPickerField(
                        groups = groups,
                        selectedGroupId = bookmarkGroupId,
                        onGroupSelected = onStarGroupSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StarButton(
                        isStarred = isStarred,
                        isSaving = isSaving,
                        onClick = onStarClick,
                        modifier = Modifier.weight(1f).height(64.dp),
                    )
                    Button(
                        onClick = { NavigationIntentHelper.launchSingleNavigation(context, poi.lat, poi.lng) },
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = "Navigate", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WikimediaAttribution(
    content: WikimediaContent?,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    if (content == null) return
    var showSources by remember { mutableStateOf(false) }
    TextButton(
        onClick = { showSources = true },
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        Text("ⓘ", style = MaterialTheme.typography.labelSmall)
    }
    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text("Sources") },
            text = {
                Column {
                    content.pageUrl?.let { url ->
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Text("Text: Wikipedia / Wikidata")
                        }
                    }
                    if (content.imageUrl != null) {
                        val label = content.imageCredit ?: "Photo details"
                        TextButton(
                            onClick = {
                                val url = content.imageSourceUrl ?: content.imageLicenseUrl
                                    ?: return@TextButton
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            enabled = content.imageSourceUrl != null || content.imageLicenseUrl != null,
                        ) {
                            Text("Photo: $label")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSources = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun DetailBottomBar(
    modifier: Modifier = Modifier,
    onEditClick: () -> Unit,
    editLabel: String,
    deleteLabel: String,
    isReadOnly: Boolean = false,
    onDeleteClick: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column {
            Button(
                onClick = {
                    if (isReadOnly) {
                        onShowMessage("Cannot edit this POI")
                    } else {
                        onEditClick()
                    }
                },
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp),
                colors = if (isReadOnly) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(editLabel)
            }
            if (!isReadOnly) {
                Button(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(deleteLabel)
                }
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColorField(label: String, colorHex: String) {
    val color = runCatching {
        // colorHex may be #AARRGGBB or #RRGGBB
        val hex = if (colorHex.length == 9 && colorHex.startsWith("#")) {
            "#${colorHex.substring(3)}"
        } else colorHex
        Color(android.graphics.Color.parseColor(hex))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(text = colorHex, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.roundToInt()} m"
    else -> "${"%.1f".format(meters / 1000)} km"
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
