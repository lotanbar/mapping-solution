package com.mappingsolution.ui.recording

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.ui.common.ColorSelectorField
import com.mappingsolution.ui.common.FormSaveButton
import kotlinx.coroutines.delay

private const val DISCARD_GUARD_THRESHOLD_M = 100.0
private const val DISCARD_COOLDOWN_SEC = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteFinalizeScreen(
    routeId: String,
    isLibraryEdit: Boolean = false,
    onDone: () -> Unit,
    viewModel: RouteFinalizeViewModel = hiltViewModel(),
) {
    LaunchedEffect(routeId) { viewModel.load(routeId) }

    val state by viewModel.state.collectAsState()

    var showDiscardDialog by remember { mutableStateOf(false) }

    fun handleBack() {
        // Discard guard only applies during post-recording finalize, not library edits
        if (!isLibraryEdit && state.distanceMeters >= DISCARD_GUARD_THRESHOLD_M) showDiscardDialog = true
        else onDone()
    }

    BackHandler { handleBack() }

    if (showDiscardDialog) {
        DiscardRecordingDialog(
            onConfirm = { showDiscardDialog = false; onDone() },
            onDismiss = { showDiscardDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isLibraryEdit) "Edit Route" else "Save Route") },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        if (state.routeId.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!isLibraryEdit && !state.isRefined) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (state.isRefining) state.refinementProgress.ifEmpty { "Refining route…" }
                            else "Not refined — you can refine this route later from the library.",
                            color = if (state.isRefining) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.isRefining) {
                            if (state.refinementProgressFraction > 0f) {
                                LinearProgressIndicator(
                                    progress = { state.refinementProgressFraction },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            TextButton(onClick = viewModel::cancelRefinement) { Text("Cancel refinement") }
                        }
                    }
                }

                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Description (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    ColorSelectorField(
                        color = state.color,
                        onColorChange = viewModel::onColorChange,
                    )
                }
            }

            FormSaveButton(
                onClick = { viewModel.save(onDone) },
                label = "Save",
                enabled = state.name.isNotBlank(),
                isSaving = state.isSaving,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DiscardRecordingDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var cooldown by remember { mutableIntStateOf(DISCARD_COOLDOWN_SEC) }
    LaunchedEffect(Unit) {
        while (cooldown > 0) { delay(1000L); cooldown-- }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard recording?") },
        text = { Text("Going back will remove this recording permanently. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = cooldown == 0) {
                Text(if (cooldown > 0) "Discard ($cooldown)" else "Discard")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}


