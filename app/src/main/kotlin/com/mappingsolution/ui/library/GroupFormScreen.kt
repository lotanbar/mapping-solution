package com.mappingsolution.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.GroupType
import com.mappingsolution.ui.common.ColorSelectorField
import com.mappingsolution.ui.common.IconCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFormScreen(
    onNavigateBack: () -> Unit,
    onNavigateToIconPicker: (currentIconKey: String) -> Unit,
    viewModel: GroupFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit Group" else "New Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Scrollable fields ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Type selector (only when creating)
                if (!viewModel.isEditing) {
                    Column {
                        Text(
                            text = "Type",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            GroupType.entries.forEachIndexed { index, type ->
                                SegmentedButton(
                                    selected = state.type == type,
                                    onClick = { viewModel.onTypeChange(type) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = GroupType.entries.size,
                                    ),
                                    label = {
                                        Text(
                                            when (type) {
                                                GroupType.POI   -> "POIs"
                                                GroupType.ROUTE -> "Routes"
                                                GroupType.PLAN  -> "Plans"
                                            }
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Name
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Description (optional)") },
                    isError = state.descriptionError != null,
                    supportingText = state.descriptionError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Icon selector
                Column {
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.iconError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedButton(
                        onClick = { onNavigateToIconPicker(state.iconKey) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(IconCatalog.iconRes(state.iconKey)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.iconKey.replace('_', ' ').replaceFirstChar { it.uppercase() })
                        Spacer(Modifier.weight(1f))
                        Text("Change", style = MaterialTheme.typography.labelMedium)
                    }
                    if (state.iconError != null) {
                        Text(
                            text = state.iconError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }

                // Shape selector
                Column {
                    Text(
                        text = "Shape",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    val shapes = listOf("pin" to "Pin", "circle" to "Circle", "square" to "Square")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        shapes.forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                selected = state.shape == key,
                                onClick = { viewModel.onShapeChange(key) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = shapes.size),
                                label = { Text(label) },
                            )
                        }
                    }
                }

                // Color selector
                Column {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.colorError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    ColorSelectorField(
                        color = state.color,
                        onColorChange = viewModel::onColorChange,
                    )
                    if (state.colorError != null) {
                        Text(
                            text = state.colorError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }
            }

            // ── Pinned bottom button ─────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Button(
                    onClick = { viewModel.save { onNavigateBack() } },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (viewModel.isEditing) "Save" else "Create")
                    }
                }
            }
        }
    }
}
