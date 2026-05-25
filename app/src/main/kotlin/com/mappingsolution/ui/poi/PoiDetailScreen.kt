package com.mappingsolution.ui.poi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.mappingsolution.ui.common.TopToast
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.MediaUtils

@Composable
fun PoiDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (poiId: String) -> Unit,
    onOpenMediaPreview: (poiId: String, index: Int, paths: List<String>) -> Unit,
    viewModel: PoiDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var topToastMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val poi = state.poi
        if (poi == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("POI not found", style = MaterialTheme.typography.bodyLarge) }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                val galleryHeight = LocalConfiguration.current.screenHeightDp.dp * 0.45f
                if (state.mediaPaths.isNotEmpty()) {
                    val mediaItems = state.mediaPaths.mapIndexed { index, path ->
                        MediaUtils.createMediaItem(path, index)
                    }
                    PoiMediaPager(
                        mediaItems = mediaItems,
                        onItemClick = { index -> onOpenMediaPreview(poi.id, index, state.mediaPaths) },
                        modifier = Modifier.height(galleryHeight),
                    )
                } else {
                    NoMediaPlaceholder(modifier = Modifier.height(galleryHeight))
                }

                PoiInfoBlock(
                    poi = poi,
                    group = state.group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 100.dp),
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column {
                    var lastDeleteClickTime by remember { mutableStateOf(0L) }
                    Button(
                        onClick = { onNavigateToEdit(poi.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit")
                    }

                    Button(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastDeleteClickTime < 2000) {
                                viewModel.deletePoi {
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        onNavigateBack()
                                    }, 100)
                                }
                            } else {
                                lastDeleteClickTime = currentTime
                                topToastMessage = "Tap again quickly to remove POI"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(52.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Remove POI")
                    }
                }
            }
        }
    }
        TopToast(message = topToastMessage, onDismiss = { topToastMessage = null })
    }
}
