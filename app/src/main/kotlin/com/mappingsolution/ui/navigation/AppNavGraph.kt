package com.mappingsolution.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.service.RecordingService
import com.mappingsolution.ui.library.GroupFormScreen
import com.mappingsolution.ui.library.GroupFormViewModel
import com.mappingsolution.ui.library.IconPickerScreen
import com.mappingsolution.ui.library.LibraryScreen
import com.mappingsolution.ui.main.MainScreen
import com.mappingsolution.ui.detail.ItemDetailScreen
import com.mappingsolution.ui.poi.PoiFormScreen
import com.mappingsolution.ui.poi.media.MediaPreviewScreen
import com.mappingsolution.ui.recording.RouteFinalizeScreen
import com.mappingsolution.ui.searchnplan.NavigationIntentHelper
import com.mappingsolution.ui.searchnplan.SearchNPlanScreen
import com.mappingsolution.ui.searchnplan.SearchNPlanViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

private const val ROUTE_MAIN = "main"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_GROUP_FORM = "group_form"
private const val ROUTE_GROUP_FORM_EDIT = "group_form/{groupId}"
private const val ROUTE_ICON_PICKER = "icon_picker"
private const val ROUTE_POI_FORM_NEW = "poi_form_new?lat={lat}&lng={lng}"
private const val ROUTE_ITEM_DETAIL = "item_detail/{type}/{id}?fromSearch={fromSearch}"
private const val ROUTE_POI_MEDIA_PREVIEW = "poi_media_preview/{poiId}?startIndex={startIndex}"
private const val ROUTE_POI_FORM_EDIT = "poi_form_edit/{poiId}"
private const val ROUTE_ROUTE_FINALIZE = "route_finalize/{routeId}"
/** Edit a saved route from the Library (no discard guard). */
private const val ROUTE_ROUTE_EDIT = "route_edit/{routeId}"
private const val ROUTE_SEARCH_N_PLAN = "search_n_plan?planId={planId}"

private const val KEY_GROUP_ID = "groupId"
private const val KEY_POI_ID = "poiId"
private const val KEY_ROUTE_ID = "routeId"
private const val KEY_DETAIL_TYPE = "type"
private const val KEY_DETAIL_ID = "id"
private const val KEY_FROM_SEARCH = "fromSearch"
private const val KEY_ADDED_DESTINATION = "added_destination"
private const val KEY_START_INDEX = "startIndex"
private const val KEY_LAT = "lat"
private const val KEY_LNG = "lng"
private const val KEY_SELECTED_ICON = "selected_icon"
private const val KEY_CURRENT_ICON = "current_icon"
private const val KEY_PLAN_ID = "planId"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_MAIN) {

        composable(ROUTE_MAIN) { backStackEntry ->
            val context = LocalContext.current
            val searchVm: SearchNPlanViewModel = hiltViewModel(backStackEntry)
            var searchSheetOpen by rememberSaveable { mutableStateOf(false) }
            var showSavePlanDialog by remember { mutableStateOf(false) }
            var planNameInput by remember { mutableStateOf("") }

            val addedDestination = backStackEntry.savedStateHandle
                .getStateFlow<PlanDestination?>(KEY_ADDED_DESTINATION, null)
                .collectAsState()
            LaunchedEffect(addedDestination.value) {
                val dest = addedDestination.value ?: return@LaunchedEffect
                searchVm.addDestinationFromDetail(dest)
                backStackEntry.savedStateHandle.remove<PlanDestination>(KEY_ADDED_DESTINATION)
                searchSheetOpen = true
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val screenHeightPx = with(density) { maxHeight.toPx() }
                val maxSheetOffsetPx = screenHeightPx * 0.50f
                var sheetOffsetPx by remember { mutableFloatStateOf(screenHeightPx) }
                val coroutineScope = rememberCoroutineScope()
                val destinations by searchVm.destinations.collectAsState()

                // When keyboard opens, push sheet to full screen; when it closes, leave as-is
                val imeBottomPx = WindowInsets.ime.getBottom(density)
                LaunchedEffect(imeBottomPx) {
                    if (searchSheetOpen && imeBottomPx > 0 && sheetOffsetPx > 0f) {
                        animate(sheetOffsetPx, 0f, animationSpec = tween(200)) { v, _ ->
                            sheetOffsetPx = v
                        }
                    }
                }

                // Keep offset in sync when screen height changes (e.g. rotation)
                LaunchedEffect(screenHeightPx) {
                    if (!searchSheetOpen) sheetOffsetPx = screenHeightPx
                }

                // Animate sheet in when opened
                LaunchedEffect(searchSheetOpen) {
                    if (searchSheetOpen) {
                        sheetOffsetPx = screenHeightPx
                        animate(screenHeightPx, 0f, animationSpec = tween(300)) { v, _ ->
                            sheetOffsetPx = v
                        }
                    }
                }

                fun dismiss() {
                    coroutineScope.launch {
                        animate(sheetOffsetPx, screenHeightPx, animationSpec = tween(300)) { v, _ ->
                            sheetOffsetPx = v
                        }
                        searchSheetOpen = false
                        searchVm.clearPreview()
                    }
                }

                BackHandler(enabled = searchSheetOpen) { dismiss() }

                MainScreen(
                    onOpenLibrary = { navController.navigate(ROUTE_LIBRARY) },
                    onAddPoi = { lat, lng -> navController.navigate("poi_form_new?lat=$lat&lng=$lng") },
                    onPoiTapped = { poiId -> navController.navigate("item_detail/poi/$poiId") },
                    onRouteTapped = { routeId -> navController.navigate("item_detail/route/$routeId") },
                    onOsmPoiTapped = { osmId -> navController.navigate("item_detail/osm_poi/$osmId") },
                    onBulkPoiTapped = { poiId -> navController.navigate("item_detail/poi/$poiId") },
                    onNavigateToFinalize = { routeId -> navController.navigate("route_finalize/$routeId") },
                    onOpenSearch = { searchSheetOpen = true },
                )

                if (searchSheetOpen) {
                    val sheetFocusManager = LocalFocusManager.current
                    val sheetKeyboardController = LocalSoftwareKeyboardController.current

                    // Sheet — pixel offset so drag stops exactly where released
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, sheetOffsetPx.roundToInt()) }
                            .statusBarsPadding(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Column {
                            // Drag handle: hides keyboard on first touch, moves sheet on drag
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.changes.any { it.pressed && !it.previousPressed }) {
                                                    sheetFocusManager.clearFocus()
                                                    sheetKeyboardController?.hide()
                                                }
                                            }
                                        }
                                    }
                                    .draggable(
                                        orientation = Orientation.Vertical,
                                        state = rememberDraggableState { delta ->
                                            sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, maxSheetOffsetPx)
                                        },
                                        onDragStopped = { velocityPx ->
                                            val dismissByFling = velocityPx > with(density) { 500.dp.toPx() }
                                            if (dismissByFling) dismiss()
                                        },
                                    )
                                    .padding(top = 8.dp, bottom = 16.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 32.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                                )
                            }

                            SearchNPlanScreen(
                                isEmbedded = true,
                                viewModel = searchVm,
                                onNavigateBack = { dismiss() },
                                onOpenDetail = { type, id ->
                                    navController.navigate("item_detail/$type/$id?fromSearch=true")
                                },
                            )
                        }
                    }

                    // Fixed action buttons — hide as soon as sheet starts sliding down past minimum
                    if (sheetOffsetPx <= maxSheetOffsetPx) Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    if (searchVm.openedFromLibrary) {
                                        searchVm.savePlan()
                                        dismiss()
                                    } else {
                                        planNameInput = ""
                                        showSavePlanDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = destinations.isNotEmpty(),
                            ) {
                                Text(if (searchVm.openedFromLibrary) "Edit Plan" else "Create Plan")
                            }
                        }
                    }
                }
            }

            if (showSavePlanDialog) {
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
                                searchVm.savePlan(planNameInput.trim().ifEmpty { null })
                                // dismiss sheet after saving
                            },
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSavePlanDialog = false }) { Text("Cancel") }
                    },
                )
            }
        }

        composable(
            route = ROUTE_POI_FORM_NEW,
            arguments = listOf(
                navArgument(KEY_LAT) { type = NavType.StringType; defaultValue = "0.0" },
                navArgument(KEY_LNG) { type = NavType.StringType; defaultValue = "0.0" },
            ),
        ) {
            PoiFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMediaPreview = { poiId, index, paths ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("media_paths", paths)
                    navController.navigate("poi_media_preview/$poiId?startIndex=$index")
                },
                onCreateGroup = { navController.navigate(ROUTE_GROUP_FORM) },
            )
        }

        composable(
            route = ROUTE_ITEM_DETAIL,
            arguments = listOf(
                navArgument(KEY_DETAIL_TYPE) { type = NavType.StringType },
                navArgument(KEY_DETAIL_ID) { type = NavType.StringType },
                navArgument(KEY_FROM_SEARCH) { type = NavType.BoolType; defaultValue = false },
            ),
        ) { backStackEntry ->
            val fromSearch = backStackEntry.arguments?.getBoolean(KEY_FROM_SEARCH) ?: false
            val context = LocalContext.current
            ItemDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditPoi = { poiId -> navController.navigate("poi_form_edit/$poiId") },
                onNavigateToEditRoute = { routeId -> navController.navigate("route_edit/$routeId") },
                onOpenMediaPreview = { poiId, index, paths ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("media_paths", paths)
                    navController.navigate("poi_media_preview/$poiId?startIndex=$index")
                },
                fromSearch = fromSearch,
                onNavigate = if (fromSearch) { lat, lng ->
                    NavigationIntentHelper.launchSingleNavigation(context, lat, lng)
                } else null,
                onAddToPlan = if (fromSearch) { dest ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_ADDED_DESTINATION, dest)
                    navController.popBackStack()
                } else null,
            )
        }

        composable(
            route = ROUTE_POI_MEDIA_PREVIEW,
            arguments = listOf(
                navArgument(KEY_POI_ID) { type = NavType.StringType },
                navArgument(KEY_START_INDEX) { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt(KEY_START_INDEX) ?: 0
            val paths = navController.previousBackStackEntry?.savedStateHandle?.get<List<String>>("media_paths") ?: emptyList()
            MediaPreviewScreen(paths = paths, startIndex = startIndex)
        }

        composable(
            route = ROUTE_POI_FORM_EDIT,
            arguments = listOf(navArgument(KEY_POI_ID) { type = NavType.StringType }),
        ) {
            PoiFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMediaPreview = { poiId, index, paths ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("media_paths", paths)
                    navController.navigate("poi_media_preview/$poiId?startIndex=$index")
                },
                onCreateGroup = { navController.navigate(ROUTE_GROUP_FORM) },
            )
        }

        composable(ROUTE_LIBRARY) {
            val context = LocalContext.current
            LibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateGroup = { navController.navigate(ROUTE_GROUP_FORM) },
                onEditGroup = { groupId -> navController.navigate("group_form/$groupId") },
                onEditPoi = { poiId -> navController.navigate("poi_form_edit/$poiId") },
                onEditRoute = { routeId -> navController.navigate("route_edit/$routeId") },
                onOpenPlan = { planId -> navController.navigate("search_n_plan?planId=$planId") },
                onContinueRecording = { routeId ->
                    context.startService(RecordingService.resumeIncompleteIntent(context, routeId))
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = false }
                    }
                },
            )
        }

        composable(ROUTE_GROUP_FORM) { backStackEntry ->
            val vm: GroupFormViewModel = hiltViewModel(backStackEntry)
            val selectedIcon = backStackEntry.savedStateHandle
                .getStateFlow(KEY_SELECTED_ICON, "").collectAsState()
            LaunchedEffect(selectedIcon.value) {
                if (selectedIcon.value.isNotEmpty()) {
                    vm.onIconChange(selectedIcon.value)
                    backStackEntry.savedStateHandle.remove<String>(KEY_SELECTED_ICON)
                }
            }
            GroupFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToIconPicker = { currentKey ->
                    backStackEntry.savedStateHandle[KEY_CURRENT_ICON] = currentKey
                    navController.navigate(ROUTE_ICON_PICKER)
                },
                viewModel = vm,
            )
        }

        composable(
            route = ROUTE_GROUP_FORM_EDIT,
            arguments = listOf(navArgument(KEY_GROUP_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val vm: GroupFormViewModel = hiltViewModel(backStackEntry)
            val selectedIcon = backStackEntry.savedStateHandle
                .getStateFlow(KEY_SELECTED_ICON, "").collectAsState()
            LaunchedEffect(selectedIcon.value) {
                if (selectedIcon.value.isNotEmpty()) {
                    vm.onIconChange(selectedIcon.value)
                    backStackEntry.savedStateHandle.remove<String>(KEY_SELECTED_ICON)
                }
            }
            GroupFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToIconPicker = { currentKey ->
                    backStackEntry.savedStateHandle[KEY_CURRENT_ICON] = currentKey
                    navController.navigate(ROUTE_ICON_PICKER)
                },
                viewModel = vm,
            )
        }

        composable(ROUTE_ICON_PICKER) {
            val formEntry = navController.previousBackStackEntry
            val currentIcon = formEntry?.savedStateHandle?.get<String>(KEY_CURRENT_ICON) ?: "marker"
            IconPickerScreen(
                currentIconKey = currentIcon,
                onIconSelected = { key ->
                    formEntry?.savedStateHandle?.set(KEY_SELECTED_ICON, key)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = ROUTE_ROUTE_FINALIZE,
            arguments = listOf(navArgument(KEY_ROUTE_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString(KEY_ROUTE_ID) ?: return@composable
            RouteFinalizeScreen(
                routeId = routeId,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = ROUTE_ROUTE_EDIT,
            arguments = listOf(navArgument(KEY_ROUTE_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString(KEY_ROUTE_ID) ?: return@composable
            RouteFinalizeScreen(
                routeId = routeId,
                isLibraryEdit = true,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = ROUTE_SEARCH_N_PLAN,
            arguments = listOf(
                navArgument(KEY_PLAN_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val vm: SearchNPlanViewModel = hiltViewModel(backStackEntry)
            val addedDestination = backStackEntry.savedStateHandle
                .getStateFlow<PlanDestination?>(KEY_ADDED_DESTINATION, null)
                .collectAsState()
            LaunchedEffect(addedDestination.value) {
                val dest = addedDestination.value ?: return@LaunchedEffect
                vm.addDestinationFromDetail(dest)
                backStackEntry.savedStateHandle.remove<PlanDestination>(KEY_ADDED_DESTINATION)
            }
            SearchNPlanScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenDetail = { type, id ->
                    navController.navigate("item_detail/$type/$id?fromSearch=true")
                },
            )
        }
    }
}
