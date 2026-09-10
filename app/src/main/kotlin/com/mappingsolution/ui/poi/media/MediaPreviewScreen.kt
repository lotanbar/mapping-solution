package com.mappingsolution.ui.poi.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.mappingsolution.data.model.MediaItem
import com.mappingsolution.data.model.MediaType
import com.mappingsolution.data.model.MediaUtils
import java.io.File
import android.net.Uri

@Composable
fun MediaPreviewScreen(
    paths: List<String>,
    startIndex: Int
) {
    val mediaItems = remember(paths) {
        paths.mapIndexed { index, path ->
            MediaItem(
                id = index.toString(),
                path = path,
                type = MediaUtils.getMediaType(path)
            )
        }
    }

    if (mediaItems.isEmpty()) return

    // Limit initialPage to avoid out-of-bounds crashes if paths changed
    val safeStartIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeStartIndex) { mediaItems.size }
    var zoomedPage by remember { mutableIntStateOf(-1) }
    
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        userScrollEnabled = zoomedPage != pagerState.currentPage,
    ) { page ->
        val item = mediaItems[page]
        when (item.type) {
            MediaType.PHOTO -> ZoomableImage(
                path = item.path,
                onZoomChanged = { zoomed ->
                    if (zoomed) zoomedPage = page else if (zoomedPage == page) zoomedPage = -1
                },
            )
            MediaType.AUDIO -> AudioPlayer(path = item.path)
        }
    }
}

@Composable
fun ZoomableImage(
    path: String,
    onZoomChanged: (Boolean) -> Unit = {},
) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var viewport by remember(path) { mutableStateOf(IntSize.Zero) }

    fun updateTransform(targetScale: Float, pan: Offset = Offset.Zero) {
        val newScale = targetScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val maxX = viewport.width * (newScale - 1f) / 2f
        val maxY = viewport.height * (newScale - 1f) / 2f
        scale = newScale
        offset = if (newScale == MIN_ZOOM) Offset.Zero else Offset(
            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
            y = (offset.y + pan.y).coerceIn(-maxY, maxY),
        )
        onZoomChanged(newScale > MIN_ZOOM)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(path) {
                detectTapGestures(
                    onDoubleTap = {
                        updateTransform(if (scale > MIN_ZOOM) MIN_ZOOM else DOUBLE_TAP_ZOOM)
                    },
                )
            },
    ) {
        AsyncImage(
            model = if (path.startsWith("http") || path.startsWith("zip://")) Uri.parse(path) else File(path),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        updateTransform(scale * zoom, pan)
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            contentScale = ContentScale.Fit
        )
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
fun AudioPlayer(path: String) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    
    DisposableEffect(Unit) {
        player.setMediaItem(ExoMediaItem.fromUri(File(path).toURI().toString()))
        player.prepare()
        onDispose { player.release() }
    }
    
    AndroidView(
        factory = { ctx -> 
            PlayerView(ctx).apply { 
                this.player = player 
                this.controllerShowTimeoutMs = 0
                this.controllerHideOnTouch = false
                this.useArtwork = true
                this.defaultArtwork = androidx.core.content.ContextCompat.getDrawable(ctx, android.R.drawable.ic_media_play)
            } 
        }, 
        update = { view ->
            view.showController()
        },
        modifier = Modifier.fillMaxSize()
    )
}
