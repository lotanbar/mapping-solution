package com.mappingsolution.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mappingsolution.BuildConfig
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.places.BULK_POI_MAX_RESULTS
import com.mappingsolution.data.places.GOOGLE_PLACES_GROUP_ID
import com.mappingsolution.data.places.GOOGLE_PLACES_MAX_RESULTS
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.places.OSM_POI_MAX_RESULTS
import com.mappingsolution.data.recording.RecordingEvent
import com.mappingsolution.data.recording.RecordingState
import com.mappingsolution.ui.common.TopToast
import com.mappingsolution.ui.main.components.BottomActionPanel
import com.mappingsolution.ui.main.components.MapComponent
import com.mappingsolution.ui.recording.RecordingViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Composable
fun MainScreen(
    onOpenLibrary: () -> Unit,
    onAddPoi: (lat: Double, lng: Double) -> Unit,
    onPoiTapped: (poiId: String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onRouteTapped: (routeId: String) -> Unit = {},
    onGooglePlaceTapped: (placeId: String) -> Unit = {},
    onOsmPoiTapped: (osmId: String) -> Unit = {},
    onBulkPoiTapped: (poiId: String) -> Unit = {},
    onNavigateToFinalize: (routeId: String) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pois by viewModel.pois.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val recordingState by recordingViewModel.state.collectAsState()
    val incompleteRoutes by viewModel.incompleteRoutes.collectAsState()
    val googlePlacesRaw by viewModel.googlePlacesRepository.pois.collectAsState()
    val osmPoisRaw by viewModel.osmPoiRepository.pois.collectAsState()
    val bulkPoisRaw by viewModel.bulkPois.collectAsState()
    val currentMapStyle by viewModel.mapStyle.collectAsState()
    val hillshadeVisible by viewModel.hillshadeVisible.collectAsState()
    val rasterLayers by viewModel.rasterLayers.collectAsState()
    val baseMapVisible by viewModel.baseMapVisible.collectAsState()
    val searchPreviewLocation by viewModel.searchPreviewLocation.collectAsState()
    val googleQuotaExhausted by viewModel.googlePlacesRepository.isQuotaExhausted.collectAsState()
    val isPoisLoading by viewModel.isPoisLoading.collectAsState()
    val osmRoadsGeoJson by viewModel.osmRoadsGeoJson.collectAsState()
    var quotaToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(googleQuotaExhausted) {
        if (googleQuotaExhausted) {
            quotaToastMessage = "Google POIs unavailable: daily API quota reached. They'll return tomorrow."
        }
    }

    // Viewport caps: 20 Google + 20 OSM + 30 imported (independent budgets).
    // Respect each source's group-level visibility toggle (set from the Library screen).
    val googleGroupVisible = groups.find { it.id == GOOGLE_PLACES_GROUP_ID }?.isVisible != false
    val osmGroupVisible = groups.find { it.id == OSM_POI_GROUP_ID }?.isVisible != false
    val googlePlaces = if (googleGroupVisible) googlePlacesRaw.take(GOOGLE_PLACES_MAX_RESULTS) else emptyList()
    val bulkPois = bulkPoisRaw.take(BULK_POI_MAX_RESULTS)
    val osmPois = if (osmGroupVisible) osmPoisRaw.take(OSM_POI_MAX_RESULTS) else emptyList()

    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var locationServicesDisabled by remember { mutableStateOf(false) }
    var locationLostDuringRecording by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var flyToTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Reset flyToTarget to null after each use so repeated requests always trigger the LaunchedEffect
    LaunchedEffect(flyToTarget) {
        if (flyToTarget != null) {
            kotlinx.coroutines.delay(500)
            flyToTarget = null
        }
    }

    // Monitor location services while a recording is in progress
    DisposableEffect(recordingState) {
        if (recordingState !is RecordingState.Active) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (!isLocationEnabled(ctx)) locationLostDuringRecording = true
            }
        }
        context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Road snapping no longer requires the map to stay alive — remove the recording-state guard.
    LaunchedEffect(recordingState) {
        if (recordingState is RecordingState.Idle) {
            viewModel.mapHolder.unregisterIfNotVisible()
        }
    }

    // Tracks which incomplete route IDs the user has already dismissed this session
    val dismissedIncompleteIds = remember { mutableSetOf<String>() }
    // The incomplete route currently shown in the recovery dialog (null = no dialog)
    var recoveryRoute by remember { mutableStateOf<Route?>(null) }

    // Show recovery dialog for the first undismissed incomplete route when recording is idle
    LaunchedEffect(incompleteRoutes, recordingState) {
        if (recordingState is RecordingState.Idle && recoveryRoute == null) {
            val candidate = incompleteRoutes.firstOrNull {
                it.id !in dismissedIncompleteIds
            }
            if (candidate != null) recoveryRoute = candidate
        }
    }

    // Collect recording stopped events and navigate to finalize screen
    LaunchedEffect(Unit) {
        recordingViewModel.events.collect { event ->
            if (event is RecordingEvent.Stopped) {
                recordingViewModel.consumeStoppedEvent()
                onNavigateToFinalize(event.routeId)
            }
        }
    }

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        recordingViewModel.startRecording()
    }

    fun checkBatteryAndStart() {
        if (recordingViewModel.needsBatteryOptimizationExemption()) {
            showBatteryDialog = true
        } else {
            recordingViewModel.startRecording()
        }
    }

    // Requests POST_NOTIFICATIONS on Android 13+, then proceeds to battery check.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless of grant — the service can still run without a visible notification.
        checkBatteryAndStart()
    }

    fun checkNotifPermAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkBatteryAndStart()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                isFetchingLocation = true
                val loc = withTimeoutOrNull(15_000L) { fetchCurrentLocation(context) }
                isFetchingLocation = false
                if (loc != null) {
                    onAddPoi(loc.first, loc.second)
                } else {
                    locationError = "Could not determine location. Try again outdoors or wait for a GPS fix."
                }
            }
        } else {
            locationError = "Location permission is required to add a POI at your current position."
        }
    }

    // Separate permission launcher for recording (location)
    val recordingPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!isLocationEnabled(context)) locationServicesDisabled = true
            else checkNotifPermAndStart()
        } else {
            locationError = "Location permission is required for route recording."
        }
    }

    recoveryRoute?.let { route ->
        AlertDialog(
            onDismissRequest = {
                dismissedIncompleteIds.add(route.id)
                recoveryRoute = null
            },
            title = { Text("Incomplete Recording") },
            text = {
                Text(
                    "\"${route.name}\" was not stopped properly.\n" +
                    "Distance so far: ${formatDistance(route.distanceMeters)}\n\n" +
                    "Would you like to continue this recording?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    recoveryRoute = null
                    recordingViewModel.resumeIncomplete(route.id)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dismissedIncompleteIds.add(route.id)
                    recoveryRoute = null
                }) { Text("Dismiss") }
            },
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("Battery Optimization") },
            text = { Text("For reliable background recording, please disable battery optimization for this app. Without this, the OS may stop recording when the screen is off.") },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    batterySettingsLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    recordingViewModel.startRecording()
                }) { Text("Continue Anyway") }
            },
        )
    }

    if (locationServicesDisabled) {
        AlertDialog(
            onDismissRequest = { locationServicesDisabled = false },
            title = { Text("Location Services Off") },
            text = { Text("GPS and network location are disabled on this device. Enable location services in your device settings to use this feature.") },
            confirmButton = {
                TextButton(onClick = {
                    locationServicesDisabled = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { locationServicesDisabled = false }) { Text("Not Now") }
            },
        )
    }

    if (locationLostDuringRecording) {
        AlertDialog(
            onDismissRequest = { locationLostDuringRecording = false },
            title = { Text("Location Services Off") },
            text = { Text("Location services were disabled while recording. The track may have gaps. Re-enable GPS to continue tracking accurately.") },
            confirmButton = {
                TextButton(onClick = {
                    locationLostDuringRecording = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { locationLostDuringRecording = false }) { Text("Dismiss") }
            },
        )
    }

    if (locationError != null) {
        AlertDialog(
            onDismissRequest = { locationError = null },
            title = { Text("Location Unavailable") },
            text = { Text(locationError!!) },
            confirmButton = {
                TextButton(onClick = { locationError = null }) { Text("OK") }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        TopToast(message = quotaToastMessage, onDismiss = { quotaToastMessage = null })
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                MapComponent(
                    pois = pois,
                    groups = groups,
                    routes = routes,
                    routePoints = routePoints,
                    googlePlaces = googlePlaces,
                    osmPois = osmPois,
                    bulkPois = bulkPois,
                    onPoiTapped = onPoiTapped,
                    onRouteTapped = onRouteTapped,
                    onGooglePlaceTapped = onGooglePlaceTapped,
                    onOsmPoiTapped = onOsmPoiTapped,
                    onBulkPoiTapped = onBulkPoiTapped,
                    onMapError = { mapError = it },
                    liveRoutePoints = (recordingState as? RecordingState.Active)?.points ?: emptyList(),
                    liveRouteColor = (recordingState as? RecordingState.Active)?.color ?: "#FFFF5722",
                    flyToLocation = flyToTarget,
                    searchPreviewLocation = searchPreviewLocation,
                    initialCamera = viewModel.initialCamera,
                    mapStyle = currentMapStyle,
                    hillshadeVisible = hillshadeVisible,
                    rasterLayers = rasterLayers,
                    baseMapVisible = baseMapVisible,
                    osmRoadsGeoJson = osmRoadsGeoJson,
                    onCameraIdle = viewModel::saveCameraPosition,
                    onBoundsChanged = { north, south, east, west, lat, lng, zoom, bearing, tilt ->
                        viewModel.onCameraChanged(lat, lng, zoom, bearing, tilt, north, south, east, west)
                    },
                    onMapReady = { map -> viewModel.mapHolder.register(map) },
                    onMapDisposed = { viewModel.mapHolder.unregister() },
                    onDoubleTap = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        when {
                            !hasPerm -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            !isLocationEnabled(context) -> locationServicesDisabled = true
                            else -> scope.launch {
                                val loc = withTimeoutOrNull(10_000L) { fetchCurrentLocation(context) }
                                if (loc != null) flyToTarget = loc
                                else locationError = "Could not get a GPS fix. Try moving outdoors or waiting a moment."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (mapError != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFFB00020),
                            ),
                        ) {
                            Text(
                                text = mapError!!,
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (BuildConfig.GOOGLE_PLACES_API_KEY.isBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = androidx.compose.ui.graphics.Color(0xFFB00020),
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                "Google Places API key is missing",
                                style = MaterialTheme.typography.labelMedium,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (isPoisLoading) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            shadowElevation = 2.dp,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Loading POIs…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                }
            }
            BottomActionPanel(
                onAddPoi = {                    val hasPerm = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPerm) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else if (!isLocationEnabled(context)) {
                        locationServicesDisabled = true
                    } else {
                        scope.launch {
                            isFetchingLocation = true
                            val loc = withTimeoutOrNull(15_000L) { fetchCurrentLocation(context) }
                            isFetchingLocation = false
                            if (loc != null) {
                                onAddPoi(loc.first, loc.second)
                            } else {
                                locationError = "Could not get a GPS fix. Try again outdoors or wait for a signal."
                            }
                        }
                    }
                },
                onRecordRoute = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    when {
                        !hasPerm -> recordingPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        !isLocationEnabled(context) -> locationServicesDisabled = true
                        else -> checkNotifPermAndStart()
                    }
                },
                recordingState = recordingState,
                onPauseRecording = { recordingViewModel.pauseRecording() },
                onResumeRecording = { recordingViewModel.resumeRecording() },
                onStopRecording = { recordingViewModel.stopRecording() },
                onColorChange = { recordingViewModel.setRecordingColor(it) },
                onOpenLibrary = onOpenLibrary,
                onOpenSearch = onOpenSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        if (isFetchingLocation) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Getting location…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun fetchCurrentLocation(context: Context): Pair<Double, Double>? {
    val tag = "LocationFetch"
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    // Try last-known location first (fast path)
    for (provider in providers) {
        runCatching {
            val enabled = lm.isProviderEnabled(provider)
            android.util.Log.d(tag, "Last-known check: provider=$provider enabled=$enabled")
            if (enabled) {
                val loc = lm.getLastKnownLocation(provider)
                android.util.Log.d(tag, "  getLastKnownLocation -> $loc")
                if (loc != null && isValidCoordinate(loc.latitude, loc.longitude)) {
                    android.util.Log.d(tag, "  Using last-known from $provider: ${loc.latitude}, ${loc.longitude}")
                    return loc.latitude to loc.longitude
                }
            }
        }.onFailure { android.util.Log.w(tag, "  Exception for $provider: $it") }
    }
    // Request a fresh fix from ALL enabled providers simultaneously; first response wins.
    val enabledProviders = providers.filter {
        runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
    }
    android.util.Log.d(tag, "Requesting fresh fix from enabled providers: $enabledProviders")
    if (enabledProviders.isEmpty()) {
        android.util.Log.w(tag, "No enabled providers — returning null")
        return null
    }

    return suspendCancellableCoroutine { cont ->
        val activeProviders = mutableListOf<String>()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                android.util.Log.d(tag, "onLocationChanged: provider=${location.provider} lat=${location.latitude} lng=${location.longitude}")
                if (!cont.isCompleted) {
                    runCatching { lm.removeUpdates(this) }
                    val result = if (isValidCoordinate(location.latitude, location.longitude))
                        location.latitude to location.longitude else null
                    android.util.Log.d(tag, "  Resuming with result=$result")
                    cont.resume(result)
                }
            }
            override fun onProviderDisabled(provider: String) {
                android.util.Log.w(tag, "onProviderDisabled: $provider (remaining: $activeProviders)")
                activeProviders.remove(provider)
                if (activeProviders.isEmpty() && !cont.isCompleted) {
                    android.util.Log.w(tag, "All providers disabled — returning null")
                    cont.resume(null)
                }
            }
        }
        for (provider in enabledProviders) {
            runCatching {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                activeProviders.add(provider)
                android.util.Log.d(tag, "Registered requestSingleUpdate for $provider")
            }.onFailure { android.util.Log.w(tag, "Failed to register $provider: $it") }
        }
        if (activeProviders.isEmpty() && !cont.isCompleted) {
            android.util.Log.w(tag, "No providers registered — returning null")
            cont.resume(null)
        }
        cont.invokeOnCancellation {
            android.util.Log.d(tag, "Coroutine cancelled — removing updates")
            runCatching { lm.removeUpdates(listener) }
        }
    }
}

private fun isValidCoordinate(lat: Double, lng: Double) =
    lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)

/** Returns true if at least one location provider (GPS or network) is enabled on the device. */
private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).any {
        runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.2f km".format(meters / 1000) else "%.0f m".format(meters)
