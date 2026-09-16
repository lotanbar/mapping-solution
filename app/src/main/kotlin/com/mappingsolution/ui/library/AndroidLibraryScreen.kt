package com.mappingsolution.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.ExportRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RasterLayerRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.map.MapLayersState
import com.mappingsolution.data.places.OsmPoiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidLibraryViewModel @Inject constructor(
    groupRepository: GroupFileRepository,
    poiRepository: PoiFileRepository,
    routeRepository: RouteFileRepository,
    planRepository: PlanFileRepository,
    exportRepository: ExportRepository,
    osmPoiRepository: OsmPoiRepository,
    bulkPoiRepository: BulkPoiRepository,
    mapLayersState: MapLayersState,
    rasterLayerRepository: RasterLayerRepository,
    jobs: AndroidLibraryJobs,
) : LibraryViewModel(
    groupRepository, poiRepository, routeRepository, planRepository, exportRepository,
    osmPoiRepository, bulkPoiRepository, mapLayersState, rasterLayerRepository, jobs,
)

/** Android host for the shared [LibraryScreen]: storage permission, file pickers and sharing. */
@Composable
fun AndroidLibraryScreen(
    onNavigateBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (String) -> Unit,
    onEditPoi: (String) -> Unit,
    onEditRoute: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onContinueRecording: (String) -> Unit,
    viewModel: AndroidLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    // ── Permission state (re-evaluated on every resume) ───────────────────
    var hasAllFilesPermission by remember { mutableStateOf(hasAllFilesAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAllFilesPermission = hasAllFilesAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showAllFilesDialog by remember { mutableStateOf(false) }
    var showGpxFilePicker by remember { mutableStateOf(false) }
    var showMbtilesPicker by remember { mutableStateOf(false) }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* ON_RESUME above will update hasAllFilesPermission */ }

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
            fileExtensions = listOf(".mbtiles"),
            onFileSelected = { file ->
                showMbtilesPicker = false
                viewModel.importMbtilesFile(Uri.fromFile(file).toString())
            },
            onDismiss = { showMbtilesPicker = false },
        )
    }

    if (showGpxFilePicker) {
        FilePickerDialog(
            initialPath = "/storage/emulated/0",
            fileExtensions = listOf(".gpx", ".zip"),
            onFileSelected = { file ->
                if (file.extension.equals("zip", ignoreCase = true)) {
                    viewModel.importZipFile(file.absolutePath)
                } else {
                    viewModel.importSingleGpxFile(file.absolutePath)
                }
                showGpxFilePicker = false
            },
            onFolderSelected = { folder ->
                viewModel.importFromFolder(folder.absolutePath)
                showGpxFilePicker = false
            },
            onDismiss = { showGpxFilePicker = false },
        )
    }

    LibraryScreen(
        onNavigateBack = onNavigateBack,
        onCreateGroup = onCreateGroup,
        onEditGroup = onEditGroup,
        onEditPoi = onEditPoi,
        onEditRoute = onEditRoute,
        onOpenPlan = onOpenPlan,
        onContinueRecording = onContinueRecording,
        viewModel = viewModel,
        onImportGpx = { if (hasAllFilesPermission) showGpxFilePicker = true else showAllFilesDialog = true },
        onImportMbtiles = { if (hasAllFilesPermission) showMbtilesPicker = true else showAllFilesDialog = true },
        onShareExport = { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export GPX"))
        },
        onShowMessage = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
    )
}

private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
