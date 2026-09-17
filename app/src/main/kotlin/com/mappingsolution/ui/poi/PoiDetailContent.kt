package com.mappingsolution.ui.poi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mappingsolution.ui.image.ZipImageFetcher
import com.mappingsolution.data.model.MediaItem
import com.mappingsolution.data.model.MediaType
import java.io.File
import android.net.Uri
import kotlin.random.Random

/**
 * Shown in place of the media pager when there are no media items.
 * The caller passes the same height/weight modifier used for [PoiMediaPager].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoMediaPlaceholder(
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onLongClick != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(42.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(52.dp),
                )
            }
            Text(
                text = if (isLoading) PoiScreenText.LOADING_CONTENT else PoiScreenText.NO_IMAGE,
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoiMediaPager(
    mediaItems: List<MediaItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveItem: ((Int) -> Unit)? = null,
    canRemoveItem: (Int) -> Boolean = { true },
    onLongClick: (() -> Unit)? = null,
    onPageChanged: (Int) -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

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
                    .combinedClickable(
                        onClick = { onItemClick(page) },
                        onLongClick = onLongClick,
                    ),
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
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(if (item.path.startsWith("http") || ZipImageFetcher.isZipUri(item.path)) item.path else File(item.path))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent),
                            onError = { android.util.Log.e("PoiMediaPager", "Image load failed: ${item.path} — ${it.result.throwable}") },
                        )
                    }
                }

                if (onRemoveItem != null && canRemoveItem(page)) {
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
