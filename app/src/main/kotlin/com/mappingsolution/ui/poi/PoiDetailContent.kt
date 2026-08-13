package com.mappingsolution.ui.poi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.mappingsolution.ui.common.resolvedTextAlign
import com.mappingsolution.ui.common.resolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.MediaItem
import com.mappingsolution.data.model.MediaType
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.ui.common.IconCatalog
import androidx.compose.ui.res.painterResource
import java.io.File
import android.net.Uri
import kotlin.random.Random

/**
 * Shown in place of the media pager when there are no media items.
 * The caller passes the same height/weight modifier used for [PoiMediaPager].
 */
@Composable
fun NoMediaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(52.dp),
            )
            Text(
                text = "No Images",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

/**
 * Full-width swipeable image/media pager. The caller controls the height via [modifier]
 * (e.g. Modifier.height(X) for the editable path or Modifier.weight(1f) for full-screen layouts).
 */
@Composable
fun PoiMediaPager(
    mediaItems: List<MediaItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveItem: ((Int) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = mediaItems[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(page) },
            ) {
                when (item.type) {
                    MediaType.AUDIO -> {
                        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                            val bars = 20
                            val barWidth = size.width / (bars * 2 - 1)
                            for (i in 0 until bars) {
                                val h = Random.nextFloat() * size.height
                                drawRect(
                                    color = Color.Gray,
                                    topLeft = androidx.compose.ui.geometry.Offset(i * barWidth * 2, (size.height - h) / 2),
                                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                        )
                    }
                    else -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (item.path.startsWith("http") || item.path.startsWith("zip://")) Uri.parse(item.path) else File(item.path))
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent),
                            onError = { android.util.Log.e("PoiMediaPager", "Image load failed: ${item.path} — ${it.result.throwable}") },
                        )
                        if (item.type == MediaType.VIDEO) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .size(28.dp),
                            )
                        }
                    }
                }

                if (onRemoveItem != null) {
                    IconButton(
                        onClick = { onRemoveItem(page) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (mediaItems.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(mediaItems.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Shows the appropriate source icon for a POI group:
 * OSM → globe, imported → layers icon, user groups → their IconCatalog icon.
 */
@Composable
fun PoiGroupSourceIcon(
    group: Group,
    size: Dp,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    when {
        group.id == OSM_POI_GROUP_ID -> {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                modifier = modifier.size(size),
                tint = tint,
            )
        }
        group.isImported -> {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                modifier = modifier.size(size),
                tint = tint,
            )
        }
        else -> {
            Icon(
                painter = painterResource(IconCatalog.iconRes(group.iconKey)),
                contentDescription = null,
                modifier = modifier.size(size),
                tint = tint,
            )
        }
    }
}

/**
 * Name / group / description info block shared across POI detail screens.
 * Order: title → description → source. Text direction auto-detects Hebrew vs English.
 */
@Composable
fun PoiInfoBlock(
    poi: Poi,
    group: Group?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = poi.name,
            style = MaterialTheme.typography.headlineSmall.copy(
                textDirection = poi.name.resolvedTextDirection(),
                textAlign = poi.name.resolvedTextAlign(),
            ),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!poi.description.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = poi.description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(18.4f, androidx.compose.ui.unit.TextUnitType.Sp),
                        textDirection = poi.description.resolvedTextDirection(),
                        textAlign = poi.description.resolvedTextAlign(),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        group?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PoiGroupSourceIcon(
                    group = it,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (it.id == OSM_POI_GROUP_ID) "Open Street Map" else it.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(16.1f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
