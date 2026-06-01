package com.mappingsolution.data.recording

import com.mappingsolution.data.recording.processing.TrackSmoother
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_ROUTE_COLOR = "#FFFF5722"

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecordingRepository @Inject constructor(
    private val routeFileRepository: RouteFileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RecordingEvent>(replay = 1, extraBufferCapacity = 0)
    val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()

    fun consumeStoppedEvent() { _events.resetReplayCache() }

    fun updateState(state: RecordingState) { _state.value = state }

    suspend fun createRoute(): Pair<String, String> {
        val name = SimpleDateFormat("dd/MM/yyyy-HH:mm", Locale.getDefault()).format(Date())
        val now = System.currentTimeMillis()
        val id = routeFileRepository.insert(
            Route(
                name = name,
                color = DEFAULT_ROUTE_COLOR,
                didUserTapStop = false,
                startedAt = now,
                checkpointAt = now,
            )
        )
        return id to name
    }

    suspend fun persistPointsSync(routeId: String, points: List<RecordingPoint>) {
        if (points.isEmpty()) return
        routeFileRepository.appendPoints(
            routeId,
            points.map { RoutePoint(ts = it.ts, lat = it.lat, lng = it.lng) },
        )
    }

    /** Persists current distance and duration to disk. Called periodically during recording
     *  so the on-disk values are up-to-date if the app is force-killed. */
    fun checkpointRoute(routeId: String, distanceMeters: Double, durationSec: Long) {
        scope.launch {
            routeFileRepository.checkpoint(routeId, distanceMeters, durationSec)
        }
    }

    fun persistPoints(routeId: String, points: List<RecordingPoint>) {
        if (points.isEmpty()) return
        scope.launch {
            routeFileRepository.appendPoints(
                routeId,
                points.map { RoutePoint(ts = it.ts, lat = it.lat, lng = it.lng) }
            )
        }
    }

    /**
     * Suspends until all previously queued async point-write coroutines have completed.
     *
     * Because [scope] is backed by a single-threaded executor ([Dispatchers.IO.limitedParallelism(1)]),
     * dispatching an empty coroutine onto it and awaiting its result guarantees that every
     * previously enqueued write has finished before this function returns.
     */
    suspend fun awaitPendingWrites() {
        scope.async { }.await()
    }

    /**
     * Applies a gentle, gap-aware, curvature-aware smoothing pass to the stored points,
     * replacing the file atomically.  Called after [awaitPendingWrites] so all in-flight
     * writes are guaranteed to have landed before we read and rewrite.
     *
     * Only straight / gradual segments are smoothed; turns, intersections, and stationary
     * suppression gaps are left untouched.  Timestamps and accumulated distance are unchanged.
     */
    suspend fun smoothTrack(routeId: String) {
        val points = routeFileRepository.getPoints(routeId)
        if (points.size < 3) return
        val smoothed = TrackSmoother.smooth(points)
        routeFileRepository.replacePoints(routeId, smoothed)
    }

    suspend fun finalizeStop(routeId: String, distanceMeters: Double, durationSec: Long) {
        val existing = routeFileRepository.getById(routeId) ?: return
        val dateStr = SimpleDateFormat("dd/MM/yyyy-HH:mm", Locale.getDefault()).format(Date(existing.startedAt))
        val fullName = "$dateStr-${formatDuration(durationSec)}-${formatDistance(distanceMeters)}"
        routeFileRepository.update(
            existing.copy(
                name = fullName,
                didUserTapStop = true,
                stoppedAt = System.currentTimeMillis(),
                distanceMeters = distanceMeters,
                durationSec = durationSec,
            )
        )
        _state.value = RecordingState.Idle
        _events.emit(RecordingEvent.Stopped(routeId))
    }

    fun updateLiveColor(color: String) {
        val current = _state.value as? RecordingState.Active ?: return
        _state.value = current.copy(color = color)
        scope.launch {
            val route = routeFileRepository.getById(current.routeId) ?: return@launch
            routeFileRepository.update(route.copy(color = color))
        }
    }

    suspend fun getIncompleteRoutes() = routeFileRepository.getIncomplete()

    /**
     * Restores [RecordingState.Active] from a previously-incomplete route so that the
     * foreground service can resume appending points to it. Call this before starting
     * location updates so the state is ready before the first fix arrives.
     */
    suspend fun resumeIncomplete(routeId: String) {
        val route = routeFileRepository.getById(routeId) ?: return
        _state.value = RecordingState.Active(
            routeId = route.id,
            autoName = route.name,
            startedAtMs = route.startedAt,
            distanceMeters = route.distanceMeters,
            color = route.color,
        )
        _events.resetReplayCache()
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000) "%.2fkm".format(meters / 1000) else "%.0fm".format(meters)
}
