package com.mappingsolution.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Plan
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.RasterLayer
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.fs.ImportResult
import com.mappingsolution.ui.common.IconCatalog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (String) -> Unit,
    onEditPoi: (String) -> Unit,
    onEditRoute: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onContinueRecording: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredPoiGroups by viewModel.filteredPoiGroups.collectAsState()
    val filteredRouteGroups by viewModel.filteredRouteGroups.collectAsState()
    val filteredPlanGroups by viewModel.filteredPlanGroups.collectAsState()
    val filteredAllGroups by viewModel.filteredAllGroups.collectAsState()
    val poisByGroup by viewModel.poisByGroup.collectAsState()
    val routesByGroup by viewModel.routesByGroup.collectAsState()
    val plansByGroup by viewModel.plansByGroup.collectAsState()
    val filteredOrphanedPois by viewModel.filteredOrphanedPois.collectAsState()
    val filteredOrphanedRoutes by viewModel.filteredOrphanedRoutes.collectAsState()
    val filteredOrphanedPlans by viewModel.filteredOrphanedPlans.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val canOrphan by viewModel.canOrphanSelection.collectAsState()
    val canUnorphan by viewModel.canUnorphanSelection.collectAsState()
    val groupPickerGroups by viewModel.groupPickerGroups.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importingFolderName by viewModel.importingFolderName.collectAsState()
    val importProgressText by viewModel.importProgressText.collectAsState()
    val importProgressFraction by viewModel.importProgressFraction.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val isMbtilesImporting by viewModel.isMbtilesImporting.collectAsState()
    val mbtilesImportProgressText by viewModel.mbtilesImportProgressText.collectAsState()
    val mbtilesImportProgressFraction by viewModel.mbtilesImportProgressFraction.collectAsState()
    val mbtilesImportResult by viewModel.mbtilesImportResult.collectAsState()
    val rasterLayers by viewModel.rasterLayers.collectAsState()
    val googlePlaceCount by viewModel.googlePlaceCount.collectAsState()
    val osmPoiCount by viewModel.osmPoiCount.collectAsState()
    val googlePlacesGroup by viewModel.googlePlacesGroup.collectAsState()
    val osmPoiGroup by viewModel.osmPoiGroup.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()
    val hillshadeVisible by viewModel.hillshadeVisible.collectAsState()

    val context = LocalContext.current

    // ── Permission state (re-evaluated on every resume) ───────────────────
    var hasAllFilesPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFilesPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showAllFilesDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showGpxFilePicker by remember { mutableStateOf(false) }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* ON_RESUME above will update hasAllFilesPermission */ }

    var showMbtilesPicker by remember { mutableStateOf(false) }

    if (showAllFilesDialog) {
        AlertDialog(
            onDismissRequest = { showAllFilesDialog = false },
            title = { Text("Allow full file access?") },
            text = {
                Text(
                    "Mapping Solution needs \"All files access\" to browse and import " +
                    "your GPX folder. Tap Open Settings, enable the toggle, then come back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAllFilesDialog = false
                    allFilesSettingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showAllFilesDialog = false }) { Text("Not now") }
            }
        )
    }

    if (showMbtilesPicker) {
        FilePickerDialog(
            initialPath = "/storage/emulated/0",
            fileExtension = ".mbtiles",
            onFileSelected = { file ->
                showMbtilesPicker = false
                viewModel.importMbtilesFile(android.net.Uri.fromFile(file))
            },
            onDismiss = { showMbtilesPicker = false },
        )
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            initialPath = "/storage/emulated/0",
            onFolderSelected = { path ->
                viewModel.importFromFolder(path)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    if (showGpxFilePicker) {
        FilePickerDialog(
            initialPath = "/storage/emulated/0",
            fileExtension = ".gpx",
            onFileSelected = { file ->
                viewModel.importSingleGpxFile(file.absolutePath)
                showGpxFilePicker = false
            },
            onFolderSelected = { folder ->
                viewModel.importFromFolder(folder.absolutePath)
                showGpxFilePicker = false
            },
            onDismiss = { showGpxFilePicker = false },
        )
    }



    // Observe export URI and fire the share chooser
    LaunchedEffect(Unit) {
        viewModel.exportUri.collect { uri: Uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export GPX"))
        }
    }

    // ── Local dialog state ────────────────────────────────────────────────
    var showDeleteGroupsDialog by remember { mutableStateOf(false) }
    var showDeleteRowsDialog by remember { mutableStateOf(false) }
    var showDeleteRasterLayersDialog by remember { mutableStateOf(false) }
    var showGroupPickerDialog by remember { mutableStateOf(false) }
    var incompleteRoute by remember { mutableStateOf<Route?>(null) }

    // Back press in selection mode clears it instead of navigating away
    BackHandler(enabled = selectionMode !is LibrarySelectionMode.None) {
        viewModel.clearSelection()
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    if (showDeleteRasterLayersDialog) {
        val count = (selectionMode as? LibrarySelectionMode.RasterLayerSelection)?.selectedIds?.size ?: 0
        DestructiveCooldownDialog(
            title = "Delete $count layer${if (count != 1) "s" else ""}",
            text = "This will permanently remove the selected raster layer${if (count != 1) "s" else ""} and delete their tile files. This cannot be undone.",
            onConfirm = { showDeleteRasterLayersDialog = false; viewModel.deleteSelectedRasterLayers() },
            onDismiss = { showDeleteRasterLayersDialog = false },
        )
    }

    importResult?.let { result ->
        ImportResultDialog(result = result, onDismiss = { viewModel.dismissImportResult() })
    }

    mbtilesImportResult?.let { result ->
        when (result) {
            is MbtilesImportResult.Success ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMbtilesImportResult() },
                    title = { Text("Import complete") },
                    text = { Text("Layer \"${result.layerName}\" imported successfully.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissMbtilesImportResult() }) { Text("OK") }
                    },
                )
            is MbtilesImportResult.Failure ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMbtilesImportResult() },
                    title = { Text("Import failed") },
                    text = { Text(result.error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissMbtilesImportResult() }) { Text("OK") }
                    },
                )
        }
    }

    if (showDeleteGroupsDialog) {
        val count = (selectionMode as? LibrarySelectionMode.GroupSelection)?.selectedIds?.size ?: 0
        DestructiveCooldownDialog(
            title = "Delete $count group${if (count != 1) "s" else ""}",
            text = "This will permanently delete the selected group${if (count != 1) "s" else ""} and all their POIs. This cannot be undone.",
            onConfirm = { showDeleteGroupsDialog = false; viewModel.deleteSelectedGroupsWithItems() },
            onDismiss = { showDeleteGroupsDialog = false },
        )
    }

    if (showDeleteRowsDialog) {
        val count = (selectionMode as? LibrarySelectionMode.RowSelection)?.selectedIds?.size ?: 0
        DestructiveCooldownDialog(
            title = "Delete $count item${if (count != 1) "s" else ""}",
            text = "This will permanently delete the selected item${if (count != 1) "s" else ""}. This cannot be undone.",
            onConfirm = { showDeleteRowsDialog = false; viewModel.deleteSelectedRows() },
            onDismiss = { showDeleteRowsDialog = false },
        )
    }

    if (showGroupPickerDialog) {
        GroupPickerDialog(
            groups = groupPickerGroups,
            onGroupPicked = { groupId ->
                viewModel.moveSelectedRowsToGroup(groupId)
                showGroupPickerDialog = false
            },
            onDismiss = { showGroupPickerDialog = false },
        )
    }

    incompleteRoute?.let { route ->
        IncompleteRouteDialog(
            route = route,
            onContinue = {
                incompleteRoute = null
                onContinueRecording(route.id)
            },
            onDismiss = { incompleteRoute = null },
        )
    }

    Scaffold(
        topBar = {
            when (val mode = selectionMode) {
                is LibrarySelectionMode.None -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = viewModel::onSearchQuery,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Box {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        "Search groups, POIs, routes…",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    },
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onCreateGroup() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.CreateNewFolder,
                                    contentDescription = "Create group",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        // ── Active import progress (always visible below search bar) ──
                        if (isImporting && importProgressText.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    text = importProgressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                if (importProgressFraction > 0f) {
                                    LinearProgressIndicator(
                                        progress = { importProgressFraction },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                }
                            }
                        }
                        if (isMbtilesImporting && mbtilesImportProgressText.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    text = mbtilesImportProgressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                if (mbtilesImportProgressFraction > 0f) {
                                    LinearProgressIndicator(
                                        progress = { mbtilesImportProgressFraction },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                }
                            }
                        }
                    }
                }
                is LibrarySelectionMode.GroupSelection -> {
                    TopAppBar(
                        title = { Text("${mode.selectedIds.size} group${if (mode.selectedIds.size != 1) "s" else ""} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            // Export selected groups
                            IconButton(onClick = { viewModel.exportSelectedGroups() }) {
                                Icon(Icons.Default.Output, contentDescription = "Export selected")
                            }
                            // Orphan action: delete groups, keep POIs
                            IconButton(onClick = { viewModel.orphanSelectedGroups() }) {
                                Icon(Icons.Default.FolderOff, contentDescription = "Orphan items")
                            }
                            // Destructive delete: groups + all POIs
                            IconButton(onClick = { showDeleteGroupsDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete groups and items",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
                is LibrarySelectionMode.RowSelection -> {
                    TopAppBar(
                        title = { Text("${mode.selectedIds.size} item${if (mode.selectedIds.size != 1) "s" else ""} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            // Export selected rows (POIs, routes, and plans)
                            IconButton(onClick = { viewModel.exportSelectedRows() }) {
                                Icon(Icons.Default.Output, contentDescription = "Export selected")
                            }
                            // Orphan: remove group from selected grouped items
                            if (canOrphan) {
                                IconButton(onClick = { viewModel.orphanSelectedRows() }) {
                                    Icon(Icons.Default.FolderOff, contentDescription = "Ungroup selected items")
                                }
                            }
                            // Un-orphan: move ungrouped items to a group
                            if (canUnorphan) {
                                IconButton(onClick = { showGroupPickerDialog = true }) {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = "Move to group")
                                }
                            }
                            // Delete selected rows
                            IconButton(onClick = { showDeleteRowsDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
                is LibrarySelectionMode.RasterLayerSelection -> {
                    TopAppBar(
                        title = { Text("${mode.selectedIds.size} layer${if (mode.selectedIds.size != 1) "s" else ""} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showDeleteRasterLayersDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete selected layers",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            // ── Map Layers ────────────────────────────────────────────────
            item(key = "map-layers-header") {
                SectionHeaderWithAction(
                    title = "Map Layers",
                    isLoading = isMbtilesImporting,
                    onAction = {
                        if (hasAllFilesPermission) showMbtilesPicker = true
                        else showAllFilesDialog = true
                    },
                )
            }
            item(key = "map-layer-satellite") {
                MapLayerRow(
                    label = "Satellite",
                    isActive = mapStyle == com.mappingsolution.data.map.MapStyle.SATELLITE,
                    onTap = { viewModel.setMapStyle(com.mappingsolution.data.map.MapStyle.SATELLITE) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "map-layer-vector") {
                MapLayerRow(
                    label = "Vector",
                    isActive = mapStyle == com.mappingsolution.data.map.MapStyle.TOPO_DARK,
                    onTap = { viewModel.setMapStyle(com.mappingsolution.data.map.MapStyle.TOPO_DARK) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "map-layer-hillshade") {
                MapLayerRow(
                    label = "Hillshading",
                    isActive = hillshadeVisible,
                    onTap = { viewModel.toggleHillshade() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            items(rasterLayers, key = { "raster-${it.id}" }) { layer ->
                val isLayerSelected = (selectionMode as? LibrarySelectionMode.RasterLayerSelection)
                    ?.selectedIds?.contains(layer.id) == true
                RasterLayerRow(
                    layer = layer,
                    isSelected = isLayerSelected,
                    selectionMode = selectionMode,
                    onTap = {
                        when (selectionMode) {
                            is LibrarySelectionMode.RasterLayerSelection -> viewModel.toggleRasterLayerSelection(layer.id)
                            else -> Unit
                        }
                    },
                    onLongPress = {
                        if (selectionMode is LibrarySelectionMode.None) {
                            viewModel.enterRasterLayerSelection(layer.id)
                        }
                    },
                    onToggleVisibility = { viewModel.toggleRasterLayerVisibility(layer.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // ── POIs ──────────────────────────────────────────────────────
            if (googlePlacesGroup != null || osmPoiGroup != null ||
                filteredPoiGroups.isNotEmpty() || filteredOrphanedPois.isNotEmpty()
            ) {
                item {
                    SectionHeaderWithAction(
                        title = "POIs",
                        isLoading = isImporting,
                        onAction = {
                            if (hasAllFilesPermission) showGpxFilePicker = true
                            else showAllFilesDialog = true
                        },
                    )
                }
                googlePlacesGroup?.let { group ->
                    item(key = "places-group") {
                        PlacesGroupRow(
                            group = group,
                            count = googlePlaceCount,
                            onToggleVisibility = { viewModel.toggleGooglePlacesVisibility() },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                osmPoiGroup?.let { group ->
                    item(key = "osm-group") {
                        PlacesGroupRow(
                            group = group,
                            count = osmPoiCount,
                            onToggleVisibility = { viewModel.toggleOsmPoisVisibility() },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                filteredPoiGroups.forEach { group ->
                    val isCollapsible = !group.isImported
                    val isExpanded = isCollapsible && group.id in expandedGroups
                    val isGroupSelected = (selectionMode as? LibrarySelectionMode.GroupSelection)
                        ?.selectedIds?.contains(group.id) == true
                    val itemCount = if (group.isBulk) group.bulkPoiCount else poisByGroup[group.id]?.size ?: 0

                    item(key = "group-${group.id}") {
                        val isGroupImporting = isImporting &&
                            importingFolderName?.equals(group.name, ignoreCase = true) == true
                        GroupHeaderRow(
                            group = group,
                            itemCount = itemCount,
                            isCollapsible = isCollapsible,
                            isExpanded = isExpanded,
                            isSelected = isGroupSelected,
                            isImporting = isGroupImporting,
                            selectionMode = selectionMode,
                            onTap = {
                                when (selectionMode) {
                                    is LibrarySelectionMode.None ->
                                        if (isCollapsible) viewModel.toggleCollapse(group.id)
                                        else onEditGroup(group.id)
                                    is LibrarySelectionMode.GroupSelection -> viewModel.toggleGroupSelection(group.id)
                                    else -> Unit
                                }
                            },
                            onLongPress = {
                                if (selectionMode is LibrarySelectionMode.None) viewModel.enterGroupSelection(group.id)
                            },
                            onEditTap = { onEditGroup(group.id) },
                            onToggleVisibility = { viewModel.toggleGroupVisibility(group) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    if (group.isImported && !group.importComplete && !isImporting) {
                        item(key = "group-incomplete-${group.id}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind { drawRect(android.graphics.Color.parseColor("#33FF9800").let { c -> Color(c) }) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = "Import incomplete — re-import to fix",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFF9800),
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    if (isExpanded) {
                        val groupPois = poisByGroup[group.id].orEmpty()
                        items(groupPois, key = { "gpoi-${it.id}" }) { poi ->
                            val isPoiSelected = (selectionMode as? LibrarySelectionMode.RowSelection)
                                ?.selectedIds?.contains(poi.id) == true
                            PoiRow(
                                poi = poi,
                                isSelected = isPoiSelected,
                                selectionMode = selectionMode,
                                indented = true,
                                onTap = {
                                    when (selectionMode) {
                                        is LibrarySelectionMode.None -> onEditPoi(poi.id)
                                        is LibrarySelectionMode.RowSelection -> viewModel.toggleRowSelection(poi.id)
                                        else -> Unit
                                    }
                                },
                                onLongPress = {
                                    if (selectionMode is LibrarySelectionMode.None) viewModel.enterRowSelection(poi.id)
                                },
                                onToggleVisibility = { viewModel.togglePoiVisibility(poi) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                        }
                    }
                }

                // ── Orphaned POIs ──────────────────────────────────────────
                items(filteredOrphanedPois, key = { "orphan-${it.id}" }) { poi ->
                    val isSelected = (selectionMode as? LibrarySelectionMode.RowSelection)
                        ?.selectedIds?.contains(poi.id) == true
                    PoiRow(
                        poi = poi,
                        isSelected = isSelected,
                        selectionMode = selectionMode,
                        indented = false,
                        onTap = {
                            when (selectionMode) {
                                is LibrarySelectionMode.None -> onEditPoi(poi.id)
                                is LibrarySelectionMode.RowSelection -> viewModel.toggleRowSelection(poi.id)
                                else -> Unit
                            }
                        },
                        onLongPress = {
                            if (selectionMode is LibrarySelectionMode.None) viewModel.enterRowSelection(poi.id)
                        },
                        onToggleVisibility = { viewModel.togglePoiVisibility(poi) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }


            // ── Plans ─────────────────────────────────────────────────────
            if (filteredPlanGroups.isNotEmpty() || filteredOrphanedPlans.isNotEmpty()) {
                item(key = "plans-header") { SectionHeader("Plans") }

                filteredPlanGroups.forEach { group ->
                    val isExpanded = group.id in expandedGroups
                    val isGroupSelected = (selectionMode as? LibrarySelectionMode.GroupSelection)
                        ?.selectedIds?.contains(group.id) == true
                    val itemCount = plansByGroup[group.id]?.size ?: 0

                    item(key = "pgroup-${group.id}") {
                        GroupHeaderRow(
                            group = group,
                            itemCount = itemCount,
                            isCollapsible = true,
                            isExpanded = isExpanded,
                            isSelected = isGroupSelected,
                            isImporting = false,
                            selectionMode = selectionMode,
                            onTap = {
                                when (selectionMode) {
                                    is LibrarySelectionMode.None -> viewModel.toggleCollapse(group.id)
                                    is LibrarySelectionMode.GroupSelection -> viewModel.toggleGroupSelection(group.id)
                                    else -> Unit
                                }
                            },
                            onLongPress = {
                                if (selectionMode is LibrarySelectionMode.None) viewModel.enterGroupSelection(group.id)
                            },
                            onEditTap = { onEditGroup(group.id) },
                            onToggleVisibility = { viewModel.toggleGroupVisibility(group) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    if (isExpanded) {
                        val groupPlans = plansByGroup[group.id].orEmpty()
                        items(groupPlans, key = { "gplan-${it.id}" }) { plan ->
                            val isPlanSelected = (selectionMode as? LibrarySelectionMode.RowSelection)
                                ?.selectedIds?.contains(plan.id) == true
                            PlanRow(
                                plan = plan,
                                isSelected = isPlanSelected,
                                selectionMode = selectionMode,
                                onTap = {
                                    when (selectionMode) {
                                        is LibrarySelectionMode.None -> onOpenPlan(plan.id)
                                        is LibrarySelectionMode.RowSelection -> viewModel.toggleRowSelection(plan.id)
                                        else -> Unit
                                    }
                                },
                                onLongPress = {
                                    if (selectionMode is LibrarySelectionMode.None) viewModel.enterRowSelection(plan.id)
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 24.dp, end = 16.dp))
                        }
                    }
                }

                items(filteredOrphanedPlans, key = { "plan-${it.id}" }) { plan ->
                    val isPlanSelected = (selectionMode as? LibrarySelectionMode.RowSelection)
                        ?.selectedIds?.contains(plan.id) == true
                    PlanRow(
                        plan = plan,
                        isSelected = isPlanSelected,
                        selectionMode = selectionMode,
                        onTap = {
                            when (selectionMode) {
                                is LibrarySelectionMode.None -> onOpenPlan(plan.id)
                                is LibrarySelectionMode.RowSelection -> viewModel.toggleRowSelection(plan.id)
                                else -> Unit
                            }
                        },
                        onLongPress = {
                            if (selectionMode is LibrarySelectionMode.None) viewModel.enterRowSelection(plan.id)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // ── Routes ────────────────────────────────────────────────────
            if (filteredRouteGroups.isNotEmpty() || filteredOrphanedRoutes.isNotEmpty()) {
                item(key = "routes-header") { SectionHeader("Routes") }

                filteredRouteGroups.forEach { group ->
                    val isExpanded = group.id in expandedGroups
                    val isGroupSelected = (selectionMode as? LibrarySelectionMode.GroupSelection)
                        ?.selectedIds?.contains(group.id) == true
                    val itemCount = routesByGroup[group.id]?.size ?: 0

                    item(key = "rgroup-${group.id}") {
                        GroupHeaderRow(
                            group = group,
                            itemCount = itemCount,
                            isCollapsible = true,
                            isExpanded = isExpanded,
                            isSelected = isGroupSelected,
                            isImporting = false,
                            selectionMode = selectionMode,
                            onTap = {
                                when (selectionMode) {
                                    is LibrarySelectionMode.None -> viewModel.toggleCollapse(group.id)
                                    is LibrarySelectionMode.GroupSelection -> viewModel.toggleGroupSelection(group.id)
                                    else -> Unit
                                }
                            },
                            onLongPress = {
                                if (selectionMode is LibrarySelectionMode.None) viewModel.enterGroupSelection(group.id)
                            },
                            onEditTap = { onEditGroup(group.id) },
                            onToggleVisibility = { viewModel.toggleGroupVisibility(group) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    if (isExpanded) {
                        val groupRoutes = routesByGroup[group.id].orEmpty()
                        items(groupRoutes, key = { "groute-${it.id}" }) { route ->
                            val isRouteSelected = (selectionMode as? LibrarySelectionMode.RowSelection)
                                ?.selectedIds?.contains(route.id) == true
                            RouteRow(
                                route = route,
                                isSelected = isRouteSelected,
                                selectionMode = selectionMode,
                                onTap = {
                                    when (selectionMode) {
                                        is LibrarySelectionMode.None -> onEditRoute(route.id)
                                        is LibrarySelectionMode.RowSelection -> viewModel.toggleRowSelection(route.id)
                                        else -> Unit
                                    }
                                },
                                onLongPress = {
                                    if (selectionMode is LibrarySelectionMode.None) viewModel.enterRowSelection(route.id)
                                },
                                onToggleVisibility = { viewModel.toggleRouteVisibility(route) },
                                onIncompleteIconTap = { incompleteRoute = route },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 24.dp, end = 16.dp))
                        }
                    }
                }

                items(filteredOrphanedRoutes, key = { "route-${it.id}" }) { route ->
                    val isSelected = (selectionMode as? LibrarySelectionMode.RowSelection)
                        ?.selectedIds?.contains(route.id) == true
                    RouteRow(
                        route = route,
                        isSelected = isSelected,
                        selectionMode = selectionMode,
                        onTap = {
                            when (selectionMode) {
                                is LibrarySelectionMode.None -> onEditRoute(route.id)
                                is LibrarySelectionMode.RowSelection -> viewModel.toggleRowSelection(route.id)
                                else -> Unit
                            }
                        },
                        onLongPress = {
                            if (selectionMode is LibrarySelectionMode.None) viewModel.enterRowSelection(route.id)
                        },
                        onToggleVisibility = { viewModel.toggleRouteVisibility(route) },
                        onIncompleteIconTap = { incompleteRoute = route },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // ── Empty state ───────────────────────────────────────────────
            if (filteredAllGroups.isEmpty() && filteredOrphanedPois.isEmpty() &&
                filteredOrphanedRoutes.isEmpty() && filteredOrphanedPlans.isEmpty() &&
                googlePlacesGroup == null && osmPoiGroup == null
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (searchQuery.isBlank()) Icons.Default.FolderOpen else Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (searchQuery.isBlank()) "Nothing here yet" else "No results for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Record a route or tap + to create a group",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionHeaderWithAction(
    title: String,
    isLoading: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        IconButton(onClick = onAction, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Input,
                    contentDescription = "Import",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ── Raster layer row ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RasterLayerRow(
    layer: RasterLayer,
    isSelected: Boolean,
    selectionMode: LibrarySelectionMode,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleVisibility: () -> Unit,
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.surface

    ListItem(
        headlineContent = { Text(layer.name, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = if (isSelected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        trailingContent = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (layer.isVisible) "Hide layer" else "Show layer",
                    tint = if (layer.isVisible) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = backgroundColor),
        modifier = Modifier.combinedClickable(
            onClick = onTap,
            onLongClick = onLongPress,
        ),
    )
}

// ── Map layer toggle row ──────────────────────────────────────────────────────

@Composable
private fun MapLayerRow(
    label: String,
    isActive: Boolean,
    onTap: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onTap),
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Icon(
                imageVector = if (isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isActive) "Visible" else "Hidden",
                tint = if (isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        },
    )
}

// ── Group header row ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeaderRow(
    group: Group,
    itemCount: Int,
    isCollapsible: Boolean,
    isExpanded: Boolean,
    isSelected: Boolean,
    isImporting: Boolean,
    selectionMode: LibrarySelectionMode,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onEditTap: () -> Unit,
    onToggleVisibility: () -> Unit,
) {
    val groupColor = parseHexColor(group.color)
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val countText = when {
        itemCount == 0 -> group.description
        group.description.isNullOrEmpty() -> "$itemCount item${if (itemCount != 1) "s" else ""}"
        else -> "${group.description} · $itemCount item${if (itemCount != 1) "s" else ""}"
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
        headlineContent = { Text(group.name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = countText?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
        },
        leadingContent = {
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind { drawCircle(primaryColor) },
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = onPrimaryColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind { drawCircle(groupColor) },
                ) {
                    Icon(
                        imageVector = IconCatalog.iconVector(group.iconKey),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
                if (isCollapsible) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (group.isVisible) Icons.Default.Visibility
                                      else Icons.Default.VisibilityOff,
                        contentDescription = if (group.isVisible) "Hide" else "Show",
                        tint = if (group.isVisible) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        },
    )
}

// ── POI row ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PoiRow(
    poi: Poi,
    isSelected: Boolean,
    selectionMode: LibrarySelectionMode,
    indented: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleVisibility: () -> Unit,
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier
            .then(if (indented) Modifier.padding(start = 24.dp) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        headlineContent = { Text(poi.name) },
        supportingContent = poi.description?.let {
            { Text(it, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = {
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Icon(Icons.Default.LocationOn, contentDescription = null)
            }
        },
        trailingContent = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (poi.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (poi.isVisible) "Hide" else "Show",
                    tint = if (poi.isVisible) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        },
    )
}

// ── Route row ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteRow(
    route: Route,
    isSelected: Boolean,
    selectionMode: LibrarySelectionMode,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleVisibility: () -> Unit,
    onIncompleteIconTap: () -> Unit,
) {
    val routeColor = parseHexColor(route.color)
    val isIncomplete = !route.didUserTapStop
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
        headlineContent = { Text(route.name) },
        supportingContent = route.description?.let {
            { Text(it, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = {
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(routeColor),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isIncomplete) {
                    IconButton(onClick = onIncompleteIconTap) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = "Incomplete recording",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (route.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (route.isVisible) "Hide" else "Show",
                        tint = if (route.isVisible) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        },
    )
}

// ── Plan row ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanRow(
    plan: Plan,
    isSelected: Boolean,
    selectionMode: LibrarySelectionMode,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val stopCount = plan.destinations.size
    val dateStr = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(plan.createdAt))
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = { Text(plan.name) },
        supportingContent = {
            Text(
                "$stopCount stop${if (stopCount != 1) "s" else ""} · $dateStr",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun DestructiveCooldownDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (seconds > 0) { delay(1_000L); seconds-- }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = seconds == 0) {
                Text(
                    if (seconds > 0) "Delete ($seconds)" else "Delete",
                    color = if (seconds == 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupPickerDialog(
    groups: List<Group>,
    onGroupPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to group") },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text("No groups available. Create a group first.")
                } else {
                    groups.forEach { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            leadingContent = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .drawBehind { drawCircle(parseHexColor(group.color)) },
                                ) {
                                    Icon(
                                        imageVector = IconCatalog.iconVector(group.iconKey),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onGroupPicked(group.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IncompleteRouteDialog(
    route: Route,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Incomplete recording") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("\"${route.name}\" was not stopped properly.")
                Spacer(Modifier.height(4.dp))
                Text("Distance: ${formatDistance(route.distanceMeters)}")
                Text("Duration: ${formatDuration(route.durationSec)}")
                Text("Started: ${formatDate(route.startedAt)}")
                Spacer(Modifier.height(8.dp))
                Text("Would you like to continue this recording?")
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    )
}

// ── Import result dialog ───────────────────────────────────────────────────────

@Composable
private fun ImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    if (result.isValidationFailure) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import cancelled — validation failed") },
            text = {
                Column {
                    Text(
                        "${result.validationErrors.size} problem${if (result.validationErrors.size != 1) "s" else ""} found. No data was imported.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        result.validationErrors.forEach { error ->
                            Text(
                                "• $error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    } else {
        val failed = result.errors.isNotEmpty() && result.poisImported == 0 && result.routesImported == 0
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (failed) "Import failed" else "Import complete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (failed) {
                        Text(
                            "An error occurred — no data was saved.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        if (result.poisImported > 0)
                            Text("${result.poisImported} POI${if (result.poisImported != 1) "s" else ""} imported")
                        if (result.routesImported > 0)
                            Text("${result.routesImported} route${if (result.routesImported != 1) "s" else ""} imported")
                        if (result.poisImported == 0 && result.routesImported == 0)
                            Text("No items found to import.")
                    }
                    if (result.filesSkipped > 0)
                        Text("${result.filesSkipped} file${if (result.filesSkipped != 1) "s" else ""} skipped")
                    if (result.errors.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            result.errors.forEach {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun parseHexColor(hex: String): Color = try {
    val cleaned = hex.trimStart('#')
    val long = cleaned.toLong(16)
    val a = if (cleaned.length == 8) ((long shr 24) and 0xFF) / 255f else 1f
    val r = ((long shr 16) and 0xFF) / 255f
    val g = ((long shr 8) and 0xFF) / 255f
    val b = (long and 0xFF) / 255f
    Color(r, g, b, a)
} catch (_: Exception) { Color.Gray }

private fun formatDistance(meters: Double): String {
    return if (meters < 1000) "%.0f m".format(meters)
    else "%.2f km".format(meters / 1000)
}

private fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

// ── Protected places group row ────────────────────────────────────────────────

@Composable
private fun PlacesGroupRow(
    group: Group,
    count: Int,
    onToggleVisibility: () -> Unit,
) {
    val groupColor = parseHexColor(group.color)
    val countText: String? = null
    ListItem(
        headlineContent = { Text(group.name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = countText?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .drawBehind { drawCircle(groupColor) },
            ) {
                Icon(
                    imageVector = IconCatalog.iconVector(group.iconKey),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (group.isVisible) Icons.Default.Visibility
                                  else Icons.Default.VisibilityOff,
                    contentDescription = if (group.isVisible) "Hide" else "Show",
                    tint = if (group.isVisible) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        },
    )
}
