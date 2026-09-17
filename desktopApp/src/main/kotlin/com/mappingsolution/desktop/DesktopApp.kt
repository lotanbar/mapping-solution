package com.mappingsolution.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mappingsolution.data.util.AppLog
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

internal sealed interface Screen {
    data object Map : Screen
    data object Library : Screen
    /** [fromSearch] is set when "Add to plan" should return the POI to that search screen. */
    data class PoiDetail(val args: PoiScreenArgs, val fromSearch: Search? = null) : Screen
    data class Search(val planId: String?, val instance: Long = System.nanoTime()) : Screen
    /** [instance] keeps each visit's ViewModel separate while the icon picker sits on top. */
    data class GroupForm(val groupId: String?, val instance: Long = System.nanoTime()) : Screen
    data class IconPicker(val form: GroupForm, val currentIconKey: String) : Screen
    data class RouteEdit(val routeId: String) : Screen
}

@Composable
internal fun FrameWindowScope.DesktopApp(container: AppContainer) {
    // A small back stack: the map is always the root.
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Map)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        AppLog.d("DesktopApp", "Message: $message")
        scope.launch { snackbar.showSnackbar(message) }
    }
    val navigate: (Screen) -> Unit = { screen ->
        AppLog.d("DesktopApp", "Screen $screen")
        backStack = backStack + screen
    }
    val goBack: () -> Unit = {
        backStack = backStack.dropLast(1).ifEmpty { listOf(Screen.Map) }
        AppLog.d("DesktopApp", "Screen ${backStack.last()}")
    }
    val unsupported = { showMessage("Not available on desktop yet") }

    Box(Modifier.fillMaxSize()) {
        when (val screen = backStack.last()) {
            Screen.Map -> MapScreen(
                container,
                onOpenLibrary = { navigate(Screen.Library) },
                onOpenSearch = { navigate(Screen.Search(planId = null)) },
                onOpenPoi = { poiId -> navigate(Screen.PoiDetail(PoiScreenArgs(type = "poi", id = poiId))) },
            )
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
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
    }
}

@Composable
private fun FrameWindowScope.DesktopPoiScreen(
    container: AppContainer,
    args: PoiScreenArgs,
    onNavigateBack: () -> Unit,
    onAddToPlan: ((PlanDestination) -> Unit)?,
    onUnsupported: () -> Unit,
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
        showBackButton = true,
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
        showBackButton = true,
    )
}

/** Google Maps directions through [stops] in order (the last one is the destination). */
private fun googleMapsDirections(stops: List<Pair<Double, Double>>): String {
    val destination = stops.last().let { (lat, lng) -> "$lat,$lng" }
    val waypoints = stops.dropLast(1).joinToString("|") { (lat, lng) -> "$lat,$lng" }
    return "https://www.google.com/maps/dir/?api=1&destination=$destination" +
        if (waypoints.isNotEmpty()) "&waypoints=$waypoints" else ""
}
