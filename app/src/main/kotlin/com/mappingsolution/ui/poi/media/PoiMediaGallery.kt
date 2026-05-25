package com.mappingsolution.ui.poi.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.mappingsolution.data.model.MediaItem
import com.mappingsolution.data.model.MediaType
import java.io.File
import kotlin.random.Random

import androidx.compose.material.icons.filled.Close

@Composable
fun PoiMediaGallery(
    mediaItems: List<MediaItem>,
    onItemClick: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
    allowRemove: Boolean = true,
) {
    if (mediaItems.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(mediaItems) { index, item ->
            val context = LocalContext.current
            var lastClickTime by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0L) }
            
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (item.type == MediaType.AUDIO) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onItemClick(index) }
            ) {
                if (item.type == MediaType.AUDIO) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val bars = 20
                        val barWidth = size.width / (bars * 2 - 1)
                        for (i in 0 until bars) {
                            val height = Random.nextFloat() * size.height
                            drawRect(
                                color = Color.Gray,
                                topLeft = androidx.compose.ui.geometry.Offset(i * barWidth * 2, (size.height - height) / 2),
                                size = androidx.compose.ui.geometry.Size(barWidth, height)
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(if (item.path.startsWith("http")) item.path else File(item.path))
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Media type icon and duration overlay
                if (item.type != MediaType.PHOTO) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (item.type) {
                                MediaType.VIDEO -> Icons.Default.Videocam
                                MediaType.AUDIO -> Icons.Default.Mic
                                else -> Icons.Default.Image
                            },
                            contentDescription = null,
                            tint = if (item.type == MediaType.AUDIO) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        item.durationMs?.let { duration ->
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(24.dp).align(Alignment.TopStart)
                    )
                }

                // Remove button
                if (allowRemove) {
                IconButton(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 2000) {
                            onRemoveItem(index)
                        } else {
                            lastClickTime = currentTime
                            android.widget.Toast.makeText(context, "Tap again quickly to remove item", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
