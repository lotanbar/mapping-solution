package com.mappingsolution.ui.poi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.model.AudioDuration
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.util.StorageManager
import com.mappingsolution.ui.searchnplan.NavigationIntentHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AndroidUnifiedPoiViewModel @Inject constructor(
    @ApplicationContext context: Context,
    poiRepository: PoiFileRepository,
    bulkPoiRepository: BulkPoiRepository,
    groupRepository: GroupFileRepository,
    osmPoiRepository: OsmPoiRepository,
    storageManager: StorageManager,
    savedStateHandle: SavedStateHandle,
) : UnifiedPoiViewModel(
    poiRepository = poiRepository,
    bulkPoiRepository = bulkPoiRepository,
    groupRepository = groupRepository,
    osmPoiRepository = osmPoiRepository,
    storageManager = storageManager,
    args = PoiScreenArgs(
        type = savedStateHandle.get<String>("type"),
        id = savedStateHandle.get<String>("id"),
        lat = savedStateHandle.get<String>("lat")?.toDoubleOrNull() ?: 0.0,
        lng = savedStateHandle.get<String>("lng")?.toDoubleOrNull() ?: 0.0,
    ),
    openPhoto = { source -> context.contentResolver.openInputStream(Uri.parse(source)) },
)

/** Android host for the shared [UnifiedPoiScreen]: camera capture and external navigation. */
@Composable
fun AndroidUnifiedPoiScreen(
    onNavigateBack: () -> Unit,
    onOpenMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit,
    onAddToPlan: (PlanDestination) -> Unit,
    onCreateGroup: () -> Unit = {},
    viewModel: AndroidUnifiedPoiViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingPhotoUri?.let { viewModel.addPhoto(it.toString(), pendingPhotoFile) }
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

    UnifiedPoiScreen(
        onNavigateBack = onNavigateBack,
        onOpenMediaPreview = onOpenMediaPreview,
        onAddToPlan = onAddToPlan,
        viewModel = viewModel,
        onNavigateTo = { lat, lng -> NavigationIntentHelper.launchSingleNavigation(context, lat, lng) },
        onAddPhoto = ::openCamera,
        onCreateGroup = onCreateGroup,
        audioDuration = AudioDuration::read,
    )
}
