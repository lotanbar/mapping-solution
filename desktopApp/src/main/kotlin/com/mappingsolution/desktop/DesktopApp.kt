package com.mappingsolution.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import androidx.compose.ui.window.FrameWindowScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.ui.detail.RouteDetailScreen
import com.mappingsolution.ui.image.ZipImageFetcher
import com.mappingsolution.ui.library.GroupFormScreen
import com.mappingsolution.ui.library.IconPickerScreen
import com.mappingsolution.ui.library.LibraryScreen
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.ui.poi.PoiScreenArgs
import com.mappingsolution.ui.poi.UnifiedPoiScreen
import com.mappingsolution.ui.recording.RouteFinalizeScreen
import com.mappingsolution.ui.searchnplan.SearchNPlanScreen
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/** Screens shown in the side panel next to the map. */
internal sealed interface Screen {
    data object Library : Screen
    /** [fromSearch] is set when "Add to plan" should return the POI to that search screen. */
    data class PoiDetail(val args: PoiScreenArgs, val fromSearch: Search? = null) : Screen
    data class Search(val planId: String?, val instance: Long = System.nanoTime()) : Screen
    /** [instance] keeps each visit's ViewModel separate while the icon picker sits on top. */
    data class GroupForm(val groupId: String?, val instance: Long = System.nanoTime()) : Screen
    data class IconPicker(val form: GroupForm, val currentIconKey: String) : Screen
    data class RouteEdit(val routeId: String) : Screen
    data class RouteDetail(val routeId: String) : Screen
}

/** Share of the window the side panel takes; phone-shaped screens need at least [PANEL_MIN_WIDTH]. */
private const val PANEL_WIDTH_FRACTION = 0.3f
private val PANEL_MIN_WIDTH = 360.dp
private val RESIZE_HANDLE_WIDTH = 6.dp

@Composable
internal fun FrameWindowScope.DesktopApp(container: AppContainer) {
    // The map always stays on screen; screens stack up in the side panel, which closes when empty.
    var panelStack by remember { mutableStateOf(listOf<Screen>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        AppLog.d("DesktopApp", "Message: $message")
        scope.launch { snackbar.showSnackbar(message) }
    }
    fun logPanel() {
        val top = panelStack.lastOrNull()
        DevAutomation.panelState = top?.toString() ?: "closed"
        AppLog.d("DesktopApp", "Screen ${top ?: "Map"}")
    }
    /** Pushes a screen on top of the panel's current one. */
    val navigate: (Screen) -> Unit = { screen ->
        panelStack = panelStack + screen
        logPanel()
    }
    /** Replaces whatever the panel shows: used for actions started from the map. */
    val open: (Screen) -> Unit = { screen ->
        panelStack = listOf(screen)
        logPanel()
    }
    val goBack: () -> Unit = {
        panelStack = panelStack.dropLast(1)
        logPanel()
    }
    val unsupported = { showMessage("Not available on desktop yet") }
    val mapCenter = remember { MapCenter() }
    /** Width the user dragged the panel to; null keeps the default. */
    var dragWidth by remember { mutableStateOf<Dp?>(null) }

    BoxWithConstraints(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            (event.key == Key.Escape && event.type == KeyEventType.KeyDown && panelStack.isNotEmpty())
                .also { if (it) goBack() }
        },
    ) {
        val minPanelWidth = (maxWidth * PANEL_WIDTH_FRACTION).coerceAtLeast(PANEL_MIN_WIDTH).coerceAtMost(maxWidth)
        // At its widest the panel fills the window, leaving just the resize handle.
        val maxPanelWidth = (maxWidth - RESIZE_HANDLE_WIDTH).coerceAtLeast(minPanelWidth)
        val panelWidth = (dragWidth ?: minPanelWidth).coerceIn(minPanelWidth, maxPanelWidth)
        val activeSection = when (val root = panelStack.firstOrNull()) {
            Screen.Library -> PanelSection.Library
            is Screen.Search -> PanelSection.Search
            is Screen.PoiDetail -> PanelSection.NewPoi.takeIf { root.args.id == null }
            else -> null
        }
        val panelOpen = panelStack.isNotEmpty()
        // The map always fills the window and the panel lies over it: MapLibre's Vulkan renderer
        // garbles the map when its view is narrower than it is tall, so the map is never resized.
        MapScreen(
            container,
            mapCenter = mapCenter,
            visibleLeft = if (panelOpen) panelWidth + RESIZE_HANDLE_WIDTH else 0.dp,
            onOpenPoi = { type, id -> open(Screen.PoiDetail(PoiScreenArgs(type = type, id = id))) },
            onOpenRoute = { routeId -> open(Screen.RouteDetail(routeId)) },
            // Clicking the map closes the panel, so root screens need no back arrow.
            onMapClick = {
                if (panelOpen) {
                    panelStack = emptyList()
                    logPanel()
                }
            },
            showMessage = showMessage,
            modifier = Modifier.fillMaxSize(),
        )
        panelStack.lastOrNull()?.let { screen ->
            Row(Modifier.fillMaxHeight()) {
                Surface(
                    modifier = Modifier.width(panelWidth).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column {
                        // The action bar stays on top of this gap, above the open screen.
                        Spacer(Modifier.height(ACTION_BAR_HEIGHT))
                        Box(Modifier.weight(1f)) {
                            PanelScreen(container, screen, panelStack.size > 1, navigate, goBack, unsupported, showMessage)
                        }
                    }
                }
                PanelResizeHandle(
                    onDrag = { delta -> dragWidth = (panelWidth + delta).coerceIn(minPanelWidth, maxPanelWidth) },
                )
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = if (panelOpen) panelWidth else 0.dp, bottom = 16.dp),
        ) { Snackbar(it) }
        ActionBar(
            active = activeSection,
            onClick = { section ->
                when {
                    // Pressing the open section's button again closes the panel.
                    section == activeSection -> {
                        panelStack = emptyList()
                        logPanel()
                    }
                    section == PanelSection.Library -> open(Screen.Library)
                    section == PanelSection.Search -> open(Screen.Search(planId = null))
                    else -> mapCenter.get()?.let { (lat, lng) ->
                        // A null ID opens the POI screen in creation mode at the given point.
                        open(Screen.PoiDetail(PoiScreenArgs(type = null, id = null, lat = lat, lng = lng)))
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/** The panel's right edge: drag it to widen the panel up to the whole window. */
@Composable
private fun PanelResizeHandle(onDrag: (Dp) -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .width(RESIZE_HANDLE_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { pixels -> onDrag(with(density) { pixels.toDp() }) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider()
    }
}

@Composable
private fun FrameWindowScope.PanelScreen(
    container: AppContainer,
    screen: Screen,
    /** Screens opened from within the panel keep a back arrow to return to the one below. */
    showBackButton: Boolean,
    navigate: (Screen) -> Unit,
    goBack: () -> Unit,
    unsupported: () -> Unit,
    showMessage: (String) -> Unit,
) {
    when (screen) {
        Screen.Library -> DesktopLibraryScreen(
            container = container,
            onNavigateBack = goBack,
            onEditPoi = { poiId -> navigate(Screen.PoiDetail(PoiScreenArgs(type = "poi", id = poiId))) },
            onCreateGroup = { navigate(Screen.GroupForm(groupId = null)) },
            onEditGroup = { groupId -> navigate(Screen.GroupForm(groupId)) },
            onEditRoute = { routeId -> navigate(Screen.RouteEdit(routeId)) },
            onOpenPlan = { planId -> navigate(Screen.Search(planId)) },
            onUnsupported = unsupported,
            showMessage = showMessage,
            showBackButton = showBackButton,
        )
        is Screen.GroupForm -> {
            val viewModel = viewModel(key = "group-form-${screen.instance}") {
                container.newGroupFormViewModel(screen.groupId)
            }
            GroupFormScreen(
                onNavigateBack = goBack,
                onNavigateToIconPicker = { key -> navigate(Screen.IconPicker(screen, key)) },
                viewModel = viewModel,
            )
        }
        is Screen.IconPicker -> {
            val formViewModel = viewModel(key = "group-form-${screen.form.instance}") {
                container.newGroupFormViewModel(screen.form.groupId)
            }
            IconPickerScreen(
                currentIconKey = screen.currentIconKey,
                onIconSelected = { key ->
                    formViewModel.onIconChange(key)
                    goBack()
                },
                onNavigateBack = goBack,
            )
        }
        is Screen.RouteDetail -> {
            val viewModel = viewModel(key = "route-detail-${screen.routeId}") {
                container.newRouteDetailViewModel(screen.routeId)
            }
            RouteDetailScreen(
                onNavigateBack = goBack,
                onNavigateToEdit = { routeId -> navigate(Screen.RouteEdit(routeId)) },
                viewModel = viewModel,
                showBackButton = showBackButton,
            )
        }
        is Screen.RouteEdit -> {
            val viewModel = viewModel(key = "route-edit-${screen.routeId}") { container.newRouteFinalizeViewModel() }
            RouteFinalizeScreen(
                routeId = screen.routeId,
                isLibraryEdit = true,
                onDone = goBack,
                viewModel = viewModel,
            )
        }
        is Screen.PoiDetail -> DesktopPoiScreen(
            container = container,
            args = screen.args,
            onNavigateBack = goBack,
            onAddToPlan = screen.fromSearch?.let { search ->
                { destination ->
                    container.searchViewModels[search.instance]?.addDestinationFromDetail(destination)
                    goBack()
                }
            },
            onUnsupported = unsupported,
            showBackButton = showBackButton,
        )
        is Screen.Search -> {
            val uriHandler = LocalUriHandler.current
            val viewModel = viewModel(key = "search-${screen.instance}") {
                container.newSearchViewModel(screen.planId).also { container.searchViewModels[screen.instance] = it }
            }
            LaunchedEffect(viewModel) {
                viewModel.results.collect { results ->
                    if (results.isNotEmpty()) AppLog.d("DesktopApp", "Search results: ${results.joinToString { it.poi.name }}")
                }
            }
            SearchNPlanScreen(
                onNavigateBack = goBack,
                onOpenDetail = { type, id -> navigate(Screen.PoiDetail(PoiScreenArgs(type = type, id = id), fromSearch = screen)) },
                viewModel = viewModel,
                onNavigateTo = { lat, lng -> uriHandler.openUri(googleMapsDirections(listOf(lat to lng))) },
                onNavigateAll = { destinations -> uriHandler.openUri(googleMapsDirections(destinations.map { it.lat to it.lng })) },
                showBackButton = showBackButton,
            )
        }
    }
}

@Composable
private fun FrameWindowScope.DesktopPoiScreen(
    container: AppContainer,
    args: PoiScreenArgs,
    onNavigateBack: () -> Unit,
    onAddToPlan: ((PlanDestination) -> Unit)?,
    onUnsupported: () -> Unit,
    showBackButton: Boolean,
) {
    val viewModel = viewModel(key = args.toString()) { container.newPoiViewModel(args) }
    val uriHandler = LocalUriHandler.current
    UnifiedPoiScreen(
        onNavigateBack = onNavigateBack,
        onOpenMediaPreview = { _, index, paths ->
            val path = paths.getOrNull(index) ?: return@UnifiedPoiScreen
            when {
                path.startsWith("http") -> uriHandler.openUri(path)
                ZipImageFetcher.isZipUri(path) -> onUnsupported()
                else -> runCatching { Desktop.getDesktop().open(File(path)) }.onFailure { onUnsupported() }
            }
        },
        onAddToPlan = onAddToPlan ?: { onUnsupported() },
        viewModel = viewModel,
        onNavigateTo = { lat, lng -> uriHandler.openUri(googleMapsDirections(listOf(lat to lng))) },
        onAddPhoto = {
            FileDialogs.openFile(window, "Add photo", setOf("jpg", "jpeg", "png", "webp", "avif", "heic"))?.let {
                viewModel.addPhoto(it.absolutePath)
            }
        },
        onCreateGroup = onUnsupported,
        showBackButton = showBackButton,
    )
}

@Composable
private fun FrameWindowScope.DesktopLibraryScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onEditPoi: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (String) -> Unit,
    onEditRoute: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onUnsupported: () -> Unit,
    showMessage: (String) -> Unit,
    showBackButton: Boolean,
) {
    val viewModel = viewModel { container.newLibraryViewModel() }
    var askImportSource by remember { mutableStateOf(false) }

    if (askImportSource) {
        AlertDialog(
            onDismissRequest = { askImportSource = false },
            title = { Text("Import GPX") },
            text = { Text("Import a single GPX or ZIP file, or a whole folder?") },
            confirmButton = {
                TextButton(onClick = {
                    askImportSource = false
                    FileDialogs.openFile(window, "Import GPX or ZIP", setOf("gpx", "zip"))?.let { file ->
                        if (file.extension.equals("zip", ignoreCase = true)) viewModel.importZipFile(file.absolutePath)
                        else viewModel.importSingleGpxFile(file.absolutePath)
                    }
                }) { Text("File…") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askImportSource = false
                    FileDialogs.chooseFolder(window, "Import GPX folder")?.let { viewModel.importFromFolder(it.absolutePath) }
                }) { Text("Folder…") }
            },
        )
    }

    LibraryScreen(
        onNavigateBack = onNavigateBack,
        onCreateGroup = onCreateGroup,
        onEditGroup = onEditGroup,
        onEditPoi = onEditPoi,
        onEditRoute = onEditRoute,
        onOpenPlan = onOpenPlan,
        onContinueRecording = { onUnsupported() },
        viewModel = viewModel,
        onImportGpx = { askImportSource = true },
        onImportMbtiles = {
            FileDialogs.openFile(window, "Import MBTiles", setOf("mbtiles"))?.let {
                viewModel.importMbtilesFile(it.absolutePath)
            }
        },
        onShareExport = { exported ->
            val target = FileDialogs.saveFile(window, "Save GPX export", exported.name)
            if (target != null) {
                exported.copyTo(target, overwrite = true)
                showMessage("Saved ${target.name}")
            }
        },
        onShowMessage = showMessage,
        showBackButton = showBackButton,
    )
}

/** Google Maps directions through [stops] in order (the last one is the destination). */
private fun googleMapsDirections(stops: List<Pair<Double, Double>>): String {
    val destination = stops.last().let { (lat, lng) -> "$lat,$lng" }
    val waypoints = stops.dropLast(1).joinToString("|") { (lat, lng) -> "$lat,$lng" }
    return "https://www.google.com/maps/dir/?api=1&destination=$destination" +
        if (waypoints.isNotEmpty()) "&waypoints=$waypoints" else ""
}
