package com.mappingsolution.ui.poi

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.MediaUtils
import com.mappingsolution.ui.common.FormSaveButton
import com.mappingsolution.ui.common.GroupPickerField
import java.io.File

@Composable
fun PoiFormScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit = { _, _, _ -> },
    onCreateGroup: () -> Unit = {},
    viewModel: PoiFormViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val groups by viewModel.groups.collectAsState()

    // Photo capture
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) pendingPhotoUri?.let { viewModel.addMediaUris(listOf(it)) } }

    // Video capture
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success -> if (success) pendingVideoUri?.let { viewModel.addMediaUris(listOf(it)) } }

    // Camera runtime permission
    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingCameraAction?.invoke()
        pendingCameraAction = null
    }

    fun launchWithCamera(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Media area at top — mirrors the detail screen layout
            val galleryHeight = LocalConfiguration.current.screenHeightDp.dp * 0.44f
            if (state.mediaPaths.isNotEmpty()) {
                val mediaItems = state.mediaPaths.mapIndexed { index, path ->
                    MediaUtils.createMediaItem(path, index)
                }
                PoiMediaPager(
                    mediaItems = mediaItems,
                    onItemClick = { index ->
                        onNavigateToMediaPreview(viewModel.poiId ?: "", index, state.mediaPaths)
                    },
                    onRemoveItem = { index -> viewModel.removeMediaPath(state.mediaPaths[index]) },
                    modifier = Modifier.height(galleryHeight),
                )
            } else {
                NoMediaPlaceholder(modifier = Modifier.height(galleryHeight))
            }

            // Camera and video capture buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        launchWithCamera {
                            val file = File(context.filesDir, "poi_photo_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingPhotoUri = uri
                            photoLauncher.launch(uri)
                        }
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(
                    onClick = {
                        launchWithCamera {
                            val file = File(context.filesDir, "poi_video_${System.currentTimeMillis()}.mp4")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingVideoUri = uri
                            videoLauncher.launch(uri)
                        }
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Form fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Description (optional)") },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                GroupPickerField(
                    groups = groups,
                    selectedGroupId = state.groupId,
                    onGroupSelected = viewModel::onGroupChange,
                    showCreateGroup = true,
                    onCreateGroup = onCreateGroup,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.mediaError != null) {
                    Text(
                        text = state.mediaError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            FormSaveButton(
                onClick = { viewModel.save { onNavigateBack() } },
                label = if (viewModel.isEditing) "Save" else "Create",
                isSaving = state.isSaving,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}
