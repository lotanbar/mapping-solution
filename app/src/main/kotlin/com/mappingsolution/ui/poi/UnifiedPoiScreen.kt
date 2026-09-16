package com.mappingsolution.ui.poi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.AudioDuration
import com.mappingsolution.data.model.MediaUtils
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.ui.common.GroupPickerField
import com.mappingsolution.ui.common.resolvedTextAlign
import com.mappingsolution.ui.common.resolvedTextDirection
import com.mappingsolution.ui.common.isRtl
import com.mappingsolution.ui.common.resolvedParagraphTextAlign
import com.mappingsolution.ui.common.resolvedParagraphTextDirection
import com.mappingsolution.ui.searchnplan.NavigationIntentHelper
import java.io.File

@Composable
fun UnifiedPoiScreen(
    onNavigateBack: () -> Unit,
    onOpenMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit,
    onAddToPlan: (PlanDestination) -> Unit,
    onCreateGroup: () -> Unit = {},
    viewModel: UnifiedPoiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmUnstar by remember { mutableStateOf(false) }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingPhotoUri?.let { viewModel.addPhoto(it, pendingPhotoFile) }
        else pendingPhotoFile?.delete()
        pendingPhotoUri = null
        pendingPhotoFile = null
    }
    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingCameraAction?.invoke()
        pendingCameraAction = null
    }

    fun openCamera() {
        val launch = {
            val file = File(context.filesDir, "poi_photo_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = file
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).also {
                pendingPhotoUri = it
                photoLauncher.launch(it)
            }
            Unit
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launch()
        } else {
            pendingCameraAction = launch
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleBack() {
        if (state.isEditing && !state.isCreation) viewModel.discardEditing() else onNavigateBack()
    }
    BackHandler(onBack = ::handleBack)

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove POI?",
            message = "This POI and its photos will be permanently removed.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                viewModel.removeCreated(onNavigateBack)
            },
            onDismiss = { confirmRemove = false },
        )
    }
    if (confirmUnstar) {
        ConfirmDialog(
            title = "Remove star?",
            message = "Your personal note, photos, and group assignment will be deleted.",
            confirmLabel = "Unstar",
            onConfirm = {
                confirmUnstar = false
                viewModel.unstar()
            },
            onDismiss = { confirmUnstar = false },
        )
    }

    Scaffold { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.sourcePoi == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "POI not found")
            }
            else -> {
                val poi = requireNotNull(state.sourcePoi)
                val shownMedia = if (state.isEditing) {
                    val immutable = if (state.isCreated || state.isCreation) emptyList() else state.media.filterNot { it.isPersonal }
                    immutable + state.draftPersonalMedia.map { UnifiedPoiMedia(it, true) }
                } else state.media
                val paths = shownMedia.map { it.path }
                var selectedMediaIndex by remember(paths) { mutableIntStateOf(0) }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    val imageHeight = (maxHeight * 0.4608f).coerceIn(230.dp, 346.dp)
                    Column(Modifier.fillMaxSize()) {
                    // 1. Image
                    Box(Modifier.fillMaxWidth().height(imageHeight)) {
                        if (shownMedia.isEmpty()) {
                            NoMediaPlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                onLongClick = if (state.isEditing) ::openCamera else null,
                                isLoading = state.isEnrichmentLoading,
                            )
                        } else {
                            PoiMediaPager(
                                mediaItems = paths.mapIndexed { index, path -> MediaUtils.createMediaItem(path, index, AudioDuration::read) },
                                onItemClick = { index -> onOpenMediaPreview(poi.id, index, paths) },
                                onRemoveItem = if (state.isEditing) ({ index ->
                                    if (shownMedia[index].isPersonal) viewModel.removeDraftPhoto(shownMedia[index].path)
                                }) else null,
                                canRemoveItem = { shownMedia[it].isPersonal },
                                onLongClick = if (state.isEditing) ::openCamera else null,
                                onPageChanged = { selectedMediaIndex = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        WikimediaImageCredit(
                            shownMedia.getOrNull(selectedMediaIndex),
                            context,
                            Modifier.align(Alignment.BottomStart),
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        if (!state.isEditing) {
                            ReadOnlyPoiContent(state)
                            WikimediaTextCredit(state, context)
                        } else {
                        // 2. Title
                        if (state.isCreated || state.isCreation) {
                            EditableTitleWithSourceIcon(
                                state = state,
                                onTitleChange = viewModel::onTitleChange,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "Title",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TitleWithSourceIcon(title = poi.name, state = state)
                            }
                        }

                        // 4. Description
                        when {
                            state.isCreated || state.isCreation -> OutlinedTextField(
                                value = state.draftDescription,
                                onValueChange = viewModel::onDescriptionChange,
                                label = { Text("Description") },
                                placeholder = { Text(PoiScreenText.NO_DESCRIPTION) },
                                minLines = 2,
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = state.draftDescription.resolvedTextDirection(),
                                    textAlign = state.draftDescription.resolvedTextAlign(),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            state.isExternal -> ExternalDescription(state, onNoteChange = viewModel::onNoteChange)
                            else -> PoiField("Description", state.sourceDescription.ifBlank { PoiScreenText.NO_DESCRIPTION })
                        }

                        WikimediaTextCredit(state, context)

                        // 5. Group
                        GroupPickerField(
                            groups = groups,
                            selectedGroupId = state.draftGroupId,
                            onGroupSelected = viewModel::onGroupChange,
                            showCreateGroup = true,
                            onCreateGroup = onCreateGroup,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        }

                        // 6. Fixed action order
                        PoiActionRow(
                            state = state,
                            onNavigate = { NavigationIntentHelper.launchSingleNavigation(context, poi.lat, poi.lng) },
                            onStar = {
                                when {
                                    !state.isStarred -> viewModel.star()
                                    viewModel.hasPersonalDataToLose() -> confirmUnstar = true
                                    else -> viewModel.unstar()
                                }
                            },
                            onRemove = { confirmRemove = true },
                            onEditOrSave = { if (state.isEditing) viewModel.save() else viewModel.startEditing() },
                            onAddToPlan = { viewModel.destination()?.let(onAddToPlan) },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyPoiContent(state: UnifiedPoiState) {
    val poi = requireNotNull(state.sourcePoi)
    TitleWithSourceIcon(title = poi.name, state = state)
    ExpandableDescription(
        value = state.sourceDescription.ifBlank {
            if (state.isEnrichmentLoading) PoiScreenText.LOADING_DESCRIPTION else PoiScreenText.NO_DESCRIPTION
        },
    )
    if (state.isStarred && state.personalNote.isNotBlank()) {
        PoiValue(state.personalNote, maxLines = 3, fontWeight = FontWeight.Medium)
    }
    PoiValue(state.personalGroup?.name ?: PoiScreenText.NO_GROUP, maxLines = 1)
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun ExpandableDescription(value: String) {
    var expanded by remember(value) { mutableStateOf(false) }
    var hasOverflow by remember(value) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDirection = value.resolvedParagraphTextDirection(),
                textAlign = value.resolvedParagraphTextAlign(),
            ),
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) hasOverflow = result.hasVisualOverflow },
        )
        if (expanded || hasOverflow) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}

@Composable
private fun TitleWithSourceIcon(title: String, state: UnifiedPoiState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title.isRtl()) {
            SourceIcon(state)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(
                textDirection = title.resolvedTextDirection(),
                textAlign = title.resolvedTextAlign(),
            ),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!title.isRtl()) {
            SourceIcon(state)
        }
    }
}

@Composable
private fun EditableTitleWithSourceIcon(
    state: UnifiedPoiState,
    onTitleChange: (String) -> Unit,
) {
    val title = state.draftTitle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title.isRtl()) SourceIcon(state)
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            isError = state.titleError != null,
            supportingText = state.titleError?.let { { Text(it) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDirection = title.resolvedTextDirection(),
                textAlign = title.resolvedTextAlign(),
            ),
            modifier = Modifier.weight(1f),
        )
        if (!title.isRtl()) SourceIcon(state)
    }
}

@Composable
private fun SourceIcon(state: UnifiedPoiState) {
    val (icon, description) = when (state.kind) {
        PoiScreenKind.CREATION, PoiScreenKind.CREATED -> Icons.Default.PersonPinCircle to "Created personally"
        PoiScreenKind.IMPORTED, PoiScreenKind.STARRED_IMPORTED -> Icons.Default.FileDownload to "Imported source"
        PoiScreenKind.OSM, PoiScreenKind.STARRED_OSM -> Icons.Default.Public to "OpenStreetMap source"
    }
    Icon(
        icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp),
    )
}

@Composable
private fun PoiValue(
    value: String,
    maxLines: Int,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        value,
        style = MaterialTheme.typography.bodyLarge.copy(
            textDirection = value.resolvedTextDirection(),
            textAlign = value.resolvedTextAlign(),
        ),
        fontWeight = fontWeight,
        modifier = Modifier.fillMaxWidth(),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PoiField(label: String, value: String) {
    val isDescription = label == "Description"
    Column(
        modifier = Modifier.fillMaxWidth().height(if (isDescription) 88.dp else 56.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = (if (label == "Title") MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge).copy(
                textDirection = value.resolvedTextDirection(),
                textAlign = value.resolvedTextAlign(),
            ),
            fontWeight = if (label == "Title") FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (isDescription) 3 else if (label == "Title") 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExternalDescription(
    state: UnifiedPoiState,
    onNoteChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PoiField("Description", state.sourceDescription.ifBlank { PoiScreenText.NO_DESCRIPTION })
        OutlinedTextField(
            value = state.draftNote,
            onValueChange = onNoteChange,
            label = { Text("Personal Note") },
            minLines = 2,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDirection = state.draftNote.resolvedTextDirection(),
                textAlign = state.draftNote.resolvedTextAlign(),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PoiActionRow(
    state: UnifiedPoiState,
    onNavigate: () -> Unit,
    onStar: () -> Unit,
    onRemove: () -> Unit,
    onEditOrSave: () -> Unit,
    onAddToPlan: () -> Unit,
) {
    val actions = state.actions
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1976D2),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (actions.navigate) PoiActionButton(Icons.Default.Navigation, "Navigate") {
                onNavigate()
            }
            if (actions.star) PoiActionButton(
                if (state.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                if (state.isStarred) "Unstar" else "Star",
            ) {
                onStar()
            }
            if (actions.remove) PoiActionButton(Icons.Default.Delete, "Remove") {
                onRemove()
            }
            if (actions.editOrSave) PoiActionButton(
                if (state.isEditing) Icons.Default.Save else Icons.Default.Edit,
                if (state.isEditing) "Save" else "Edit",
            ) {
                onEditOrSave()
            }
            if (actions.addToPlan) PoiActionButton(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Plan") {
                onAddToPlan()
            }
        }
    }
}

@Composable
private fun PoiActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun WikimediaImageCredit(media: UnifiedPoiMedia?, context: android.content.Context, modifier: Modifier = Modifier) {
    val url = media?.imageSourceUrl ?: return
    Text(
        "Photo: ${media.imageCredit ?: "Wikimedia Commons"}",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { openUrl(context, url) }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun WikimediaTextCredit(state: UnifiedPoiState, context: android.content.Context) {
    val url = state.wikimedia?.pageUrl ?: return
    Text(
        if (url.contains("wikipedia.org", ignoreCase = true)) {
            "Read full article on Wikipedia"
        } else {
            "View source on Wikidata"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { openUrl(context, url) },
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
