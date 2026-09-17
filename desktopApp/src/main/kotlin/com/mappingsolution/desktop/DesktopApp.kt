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
import com.mappingsolution.ui.library.LibraryScreen
import com.mappingsolution.ui.poi.PoiScreenArgs
import com.mappingsolution.ui.poi.UnifiedPoiScreen
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

internal sealed interface Screen {
    data object Map : Screen
    data object Library : Screen
    data class PoiDetail(val args: PoiScreenArgs) : Screen
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
                onOpenPoi = { poiId -> navigate(Screen.PoiDetail(PoiScreenArgs(type = "poi", id = poiId))) },
            )
            Screen.Library -> DesktopLibraryScreen(
                container = container,
                onNavigateBack = goBack,
                onEditPoi = { poiId -> navigate(Screen.PoiDetail(PoiScreenArgs(type = "poi", id = poiId))) },
                onUnsupported = unsupported,
                showMessage = showMessage,
            )
            is Screen.PoiDetail -> DesktopPoiScreen(
                container = container,
                args = screen.args,
                onNavigateBack = goBack,
                onUnsupported = unsupported,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
    }
}

@Composable
private fun FrameWindowScope.DesktopPoiScreen(
    container: AppContainer,
    args: PoiScreenArgs,
    onNavigateBack: () -> Unit,
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
        onAddToPlan = { onUnsupported() },
        viewModel = viewModel,
        onNavigateTo = { lat, lng -> uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng") },
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
        onCreateGroup = onUnsupported,
        onEditGroup = { onUnsupported() },
        onEditPoi = onEditPoi,
        onEditRoute = { onUnsupported() },
        onOpenPlan = { onUnsupported() },
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
