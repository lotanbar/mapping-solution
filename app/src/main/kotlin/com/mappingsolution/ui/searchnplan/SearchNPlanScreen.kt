package com.mappingsolution.ui.searchnplan

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.data.model.SearchResult
import com.mappingsolution.ui.searchnplan.components.SearchResultRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchNPlanScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: ((type: String, id: String) -> Unit)? = null,
    isEmbedded: Boolean = false,
    onMinContentHeightChanged: ((Int) -> Unit)? = null,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: SearchNPlanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val destinations by viewModel.destinations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeRowIndex by viewModel.activeRowIndex.collectAsState()

    var showSavePlanDialog by remember { mutableStateOf(false) }
    var planNameInput by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()

    // Track rendered heights of minimum-content items for dynamic min-height reporting
    val itemHeights = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) {
        snapshotFlow {
            destinations.sumOf { itemHeights["dest_${it.id}"] ?: 0 } +
                (itemHeights["ghost"] ?: 0) +
                results.take(3).sumOf { itemHeights["result_${it.poi.id}"] ?: 0 }
        }.collect { onMinContentHeightChanged?.invoke(it) }
    }

    val destinationsList = remember { derivedStateOf { destinations } }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val list = destinationsList.value
        val fromIndex = list.indexOfFirst { "dest_${it.id}" == from.key }
        val toIndex = list.indexOfFirst { "dest_${it.id}" == to.key }
        if (fromIndex != -1 && toIndex != -1) viewModel.moveDestination(fromIndex, toIndex)
    }

    // Drag handles only visible when ≥2 destinations and no row is being edited
    val showDragHandles = destinations.size >= 2 && activeRowIndex == null

    // Back gesture aborts active search instead of navigating away
    BackHandler(enabled = activeRowIndex != null) {
        viewModel.deactivateRow()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            if (!isEmbedded) {
                TopAppBar(
                    title = { Text("Search & Plan") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .then(
                    if (activeRowIndex != null) Modifier.pointerInput(activeRowIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                pass = PointerEventPass.Final,
                                requireUnconsumed = false,
                            )
                            if (!down.isConsumed) {
                                val up = awaitPointerEvent(PointerEventPass.Final)
                                if (up.changes.any { it.changedToUp() && !it.isConsumed }) {
                                    focusManager.clearFocus()
                                    viewModel.deactivateRow()
                                }
                            }
                        }
                    } else Modifier,
                ),
        ) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomContentPadding),
            ) {
                // ── Filled destination rows ──────────────────────────────────
                destinations.forEachIndexed { index, dest ->
                    val isActive = activeRowIndex == index

                    when {
                        isActive -> {
                            // Active search field for this slot
                            item(key = "dest_${dest.id}") {
                                Box(Modifier.fillMaxWidth().onSizeChanged { itemHeights["dest_${dest.id}"] = it.height }) {
                                    DestinationInputRow(
                                        value = query,
                                        isActive = true,
                                        focusRequester = focusRequester,
                                        onValueChange = viewModel::onQueryChange,
                                    )
                                }
                            }
                            items(results, key = { "result_${it.poi.id}" }) { result ->
                                Box(Modifier.fillMaxWidth().onSizeChanged { itemHeights["result_${result.poi.id}"] = it.height }) {
                                    SearchResultRow(
                                        result = result,
                                        onNavigate = {
                                            NavigationIntentHelper.launchSingleNavigation(
                                                context, result.poi.lat, result.poi.lng,
                                            )
                                        },
                                        onAddToPlan = { viewModel.addDestination(result) },
                                        onOpenDetail = onOpenDetail?.let { callback ->
                                            {
                                                viewModel.selectResultForPreview(result)
                                                val type = when (result) {
                                                    is SearchResult.PersonalPoi -> "poi"
                                                    is SearchResult.ImportedPoi -> "poi"
                                                    is SearchResult.OsmPoi -> "osm_poi"
                                                }
                                                callback(type, result.poi.id)
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider()
                            }
                        }

                        activeRowIndex != null && index > activeRowIndex!! -> {
                            // Row below the active slot — hidden while editing
                        }

                        activeRowIndex != null -> {
                            // Row above the active slot — same visual as planning mode but grayed out
                            item(key = "dest_${dest.id}") {
                                Box(Modifier.fillMaxWidth().onSizeChanged { itemHeights["dest_${dest.id}"] = it.height }) {
                                    DestinationInputRow(
                                        value = dest.name,
                                        isActive = false,
                                        showDragHandle = destinations.size >= 2,
                                        onRemove = {},
                                        onTap = {},
                                        dimmed = true,
                                    )
                                }
                            }
                        }

                        else -> {
                            // Planning mode – reorderable input box
                            item(key = "dest_${dest.id}") {
                                Box(Modifier.fillMaxWidth().onSizeChanged { itemHeights["dest_${dest.id}"] = it.height }) {
                                    ReorderableItem(reorderableState, key = "dest_${dest.id}") { isDragging ->
                                        val elevation by animateDpAsState(
                                            targetValue = if (isDragging) 4.dp else 0.dp,
                                            label = "drag_elevation",
                                        )
                                        Surface(shadowElevation = elevation) {
                                            DestinationInputRow(
                                                value = dest.name,
                                                isActive = false,
                                                showDragHandle = showDragHandles,
                                                dragHandleModifier = Modifier.draggableHandle(),
                                                onTap = { viewModel.activateRow(index, prefill = dest.name) },
                                                onRemove = { viewModel.removeDestination(dest.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Ghost row — hidden while any row is being edited ─────────
                val isGhostActive = activeRowIndex == destinations.size
                if (activeRowIndex == null || isGhostActive) {
                    item(key = "ghost") {
                        Box(Modifier.fillMaxWidth().onSizeChanged { itemHeights["ghost"] = it.height }) {
                            DestinationInputRow(
                                value = if (isGhostActive) query else "",
                                isActive = isGhostActive,
                                placeholder = if (destinations.isEmpty()) "Add a destination…" else "Add another destination…",
                                focusRequester = if (isGhostActive) focusRequester else null,
                                onValueChange = viewModel::onQueryChange,
                                onTap = if (!isGhostActive) ({ viewModel.activateRow(destinations.size) }) else null,
                            )
                        }
                    }
                    if (isGhostActive) {
                        items(results, key = { "result_${it.poi.id}" }) { result ->
                            Box(Modifier.fillMaxWidth().onSizeChanged { itemHeights["result_${result.poi.id}"] = it.height }) {
                                SearchResultRow(
                                    result = result,
                                    onNavigate = {
                                        NavigationIntentHelper.launchSingleNavigation(
                                            context, result.poi.lat, result.poi.lng,
                                        )
                                    },
                                    onAddToPlan = { viewModel.addDestination(result) },
                                    onOpenDetail = onOpenDetail?.let { callback ->
                                        {
                                            viewModel.selectResultForPreview(result)
                                            val type = when (result) {
                                                is SearchResult.PersonalPoi -> "poi"
                                                is SearchResult.ImportedPoi -> "poi"
                                                is SearchResult.OsmPoi -> "osm_poi"
                                            }
                                            callback(type, result.poi.id)
                                        }
                                    },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }

            }

            // ── Rows below the active slot — dimmed, pinned above keyboard ──
            if (activeRowIndex != null) {
                val afterActive = destinations.drop(activeRowIndex!! + 1)
                afterActive.forEach { dest ->
                    DestinationInputRow(
                        value = dest.name,
                        isActive = false,
                        showDragHandle = destinations.size >= 2,
                        onRemove = {},
                        onTap = {},
                        dimmed = true,
                    )
                }
            }

            // ── Action buttons (non-embedded only — embedded mode uses AppNavGraph overlay) ──
            if (!isEmbedded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { NavigationIntentHelper.launchNavigation(context, destinations) },
                        modifier = Modifier.weight(1f),
                        enabled = destinations.isNotEmpty(),
                    ) {
                        Text("Navigate")
                    }
                    OutlinedButton(
                        onClick = {
                            if (viewModel.openedFromLibrary) {
                                viewModel.savePlan()
                                onNavigateBack()
                            } else {
                                planNameInput = ""
                                showSavePlanDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = destinations.isNotEmpty(),
                    ) {
                        Text(if (viewModel.openedFromLibrary) "Edit Plan" else "Create Plan")
                    }
                }
            }
        }  // end Column
    }

    if (!isEmbedded && showSavePlanDialog) {
        AlertDialog(
            onDismissRequest = { showSavePlanDialog = false },
            title = { Text("Create plan") },
            text = {
                OutlinedTextField(
                    value = planNameInput,
                    onValueChange = { planNameInput = it },
                    label = { Text("Plan name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSavePlanDialog = false
                        viewModel.savePlan(planNameInput.trim().ifEmpty { null })
                        onNavigateBack()
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePlanDialog = false }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(activeRowIndex) {
        if (activeRowIndex != null) {
            kotlinx.coroutines.delay(50)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

}

/**
 * A single destination slot rendered as an OutlinedTextField.
 * When [isActive], the field is editable and receives focus.
 * When inactive, it is read-only with a transparent tap layer so the user can tap to re-search.
 * The remove button lives outside the field to avoid being intercepted by the tap overlay.
 */
@Composable
private fun DestinationInputRow(
    value: String,
    isActive: Boolean,
    placeholder: String = "Search for a destination…",
    onValueChange: (String) -> Unit = {},
    onTap: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    dimmed: Boolean = false,
) {
    val dimColor = Color(0xFFBDBDBD)

    Box {
        CompositionLocalProvider(LocalContentColor provides if (dimmed) dimColor else LocalContentColor.current) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    modifier = dragHandleModifier,
                )
                Spacer(Modifier.width(4.dp))
            }

            val trailingIcon: (@Composable () -> Unit)? = if (isActive && value.isNotEmpty()) {
                { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "Clear") } }
            } else null

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(placeholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = trailingIcon,
                    readOnly = !isActive,
                    singleLine = true,
                    colors = if (dimmed) OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = dimColor,
                        unfocusedTextColor = dimColor,
                        unfocusedLeadingIconColor = dimColor,
                        unfocusedTrailingIconColor = dimColor,
                        unfocusedPlaceholderColor = dimColor,
                    ) else OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
                // Transparent overlay on read-only fields so taps route to our handler
                if (!isActive && onTap != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(onTap) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.pressed }) {
                                            event.changes.forEach { it.consume() }
                                            onTap()
                                        }
                                    }
                                }
                            },
                    )
                }
            }

            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove destination")
                }
            }
        }
        }  // end CompositionLocalProvider

        // Invisible touch-blocker when dimmed — no visual painting
        if (dimmed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }  // end outer Box
}

