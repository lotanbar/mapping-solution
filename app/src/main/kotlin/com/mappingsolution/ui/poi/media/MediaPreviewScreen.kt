package com.mappingsolution.ui.poi.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Color.Black)) { page ->
        val item = mediaItems[page]
        when (item.type) {
            MediaType.PHOTO -> ZoomableImage(path = item.path)
            MediaType.VIDEO -> VideoPlayer(path = item.path, isAudio = false)
            MediaType.AUDIO -> VideoPlayer(path = item.path, isAudio = true)
        }
    }
}

@Composable
fun ZoomableImage(path: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = if (path.startsWith("http") || path.startsWith("zip://")) Uri.parse(path) else File(path),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun VideoPlayer(path: String, isAudio: Boolean = false) {
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
                if (isAudio) {
                    this.controllerShowTimeoutMs = 0
                    this.controllerHideOnTouch = false
                    this.useArtwork = true
                    this.defaultArtwork = androidx.core.content.ContextCompat.getDrawable(ctx, android.R.drawable.ic_media_play)
                }
            } 
        }, 
        update = { view ->
            if (isAudio) {
                view.showController()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
