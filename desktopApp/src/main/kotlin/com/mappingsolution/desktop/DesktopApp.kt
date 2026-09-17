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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.ui.library.LibraryScreen
import kotlinx.coroutines.launch

private enum class Screen { MAP, LIBRARY }

@Composable
internal fun FrameWindowScope.DesktopApp(container: AppContainer) {
    var screen by remember { mutableStateOf(Screen.MAP) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        AppLog.d("DesktopApp", "Message: $message")
        scope.launch { snackbar.showSnackbar(message) }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.MAP -> MapScreen(container, onOpenLibrary = {
                AppLog.d("DesktopApp", "Screen LIBRARY")
                screen = Screen.LIBRARY
            })
            Screen.LIBRARY -> DesktopLibraryScreen(
                container = container,
                onNavigateBack = {
                    AppLog.d("DesktopApp", "Screen MAP")
                    screen = Screen.MAP
                },
                onUnsupported = { showMessage("Not available on desktop yet") },
                showMessage = showMessage,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
    }
}

@Composable
private fun FrameWindowScope.DesktopLibraryScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
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
        onEditPoi = { onUnsupported() },
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
