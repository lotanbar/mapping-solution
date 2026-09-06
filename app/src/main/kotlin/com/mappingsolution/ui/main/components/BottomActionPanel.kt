package com.mappingsolution.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mappingsolution.data.recording.RecordingState

@Composable
fun BottomActionPanel(
    onAddPoi: () -> Unit,
    onRecordRoute: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit = {},
    recordingState: RecordingState = RecordingState.Idle,
    onPauseRecording: () -> Unit = {},
    onResumeRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onColorChange: (String) -> Unit = {},
    isPoisLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isRecording = recordingState is RecordingState.Active
    var paneExpanded by remember { mutableStateOf(false) }

    // Keep the last Active state so the exit animation can still render the pane
    // even after recordingState has already flipped back to Idle.
    var lastActiveState by remember { mutableStateOf<RecordingState.Active?>(null) }
    if (recordingState is RecordingState.Active) lastActiveState = recordingState

    // Collapse the pane automatically when recording ends; expand automatically when it starts
    LaunchedEffect(isRecording) {
        paneExpanded = isRecording
    }

    // Flashing animation for the record button while recording is active
    val infiniteTransition = rememberInfiniteTransition(label = "recording-flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flash-alpha",
    )
    val recordIconTint = if (isRecording) Color.Red.copy(alpha = flashAlpha) else Color.White

    Column(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            if (isPoisLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF2196F3),
                    trackColor = Color.Transparent,
                )
            }
        }
        // Main 4-button row — always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAddPoi, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = "Add POI at current location",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }

            IconButton(
                onClick = {
                    if (isRecording) paneExpanded = !paneExpanded else onRecordRoute()
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = if (isRecording) "Recording in progress — tap to show details" else "Record a route",
                    modifier = Modifier.size(32.dp),
                    tint = recordIconTint,
                )
            }

            IconButton(onClick = onOpenLibrary, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Open library",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }

            IconButton(onClick = onOpenSearch, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search & Plan",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }
        }

        // Collapsible recording details pane
        AnimatedVisibility(
            visible = isRecording && paneExpanded,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            lastActiveState?.let { active ->
                RecordingPane(
                    state = active,
                    onPause = onPauseRecording,
                    onResume = onResumeRecording,
                    onStop = onStopRecording,
                    onColorChange = onColorChange,
                )
            }
        }
    }
}
