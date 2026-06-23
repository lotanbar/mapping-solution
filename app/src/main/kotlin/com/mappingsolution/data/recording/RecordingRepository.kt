package com.mappingsolution.data.recording

import com.mappingsolution.data.recording.processing.MapMatcher
import com.mappingsolution.data.recording.processing.MatchObservation
import com.mappingsolution.data.recording.processing.OsmRoadCache
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEFAULT_ROUTE_COLOR = "#FFFF5722"

/** Tolerance for deciding the smoothed track covers the committed track (force-kill safety). */
private const val SMOOTHED_COVERAGE_TOLERANCE_MS = 30_000L

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecordingRepository @Inject constructor(
    private val routeFileRepository: RouteFileRepository,
    private val osmRoadCache: OsmRoadCache,
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

    /** Persists raw Kalman-smoothed samples (position + GPS heading/speed/accuracy) to
     *  `smoothed.jsonl` for the Stop map-match pass. */
    fun persistSmoothedSamples(routeId: String, samples: List<SmoothedSample>) {
        if (samples.isEmpty()) return
        scope.launch {
            routeFileRepository.appendSmoothedSamples(routeId, samples)
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
     * Stop pass: re-matches the whole trip against the OSM road network with a full-trajectory
     * HMM/Viterbi [MapMatcher] (Newson & Krumm), then applies a light [TrackSmoother] finishing
     * pass and replaces `points.jsonl` atomically.
     *
     * Input is the clean `smoothed.jsonl` (raw Kalman output, before live matching) so the final
     * result isn't biased by the provisional live commits; falls back to `points.jsonl` for older
     * recordings that predate smoothed capture. Ensures every tile the route crosses is loaded
     * first so coverage is complete even for tiles evicted during a long recording.
     *
     * Each smoothed sample carries its GPS heading/speed/accuracy, which are fed into the matcher
     * as [MatchObservation] features — the bearing prior and per-fix emission sigma are what let the
     * final match pick the correct road and turn at junctions instead of the merely-nearest one.
     *
     * Returns the authoritative distance (metres) recomputed from the matched geometry, or null
     * if there was nothing to match (caller keeps the provisional live distance).
     */
    suspend fun mapMatchTrack(routeId: String): Double? {
        val smoothed = routeFileRepository.getSmoothedSamples(routeId)
        val committed = routeFileRepository.getPoints(routeId)
        // Prefer the clean smoothed track (with full observation features), but only if it actually
        // covers the whole recording. After a force-kill the smoothed write buffer may have lost its
        // tail, in which case the already-persisted committed points span more of the trip and must
        // not be replaced by a shorter re-match (those carry no heading/speed, so defaults apply).
        val useSmoothed = when {
            smoothed.size < 2 -> false
            committed.size < 2 -> true
            else -> smoothedCoversCommitted(smoothed, committed)
        }
        val observations: List<MatchObservation> = if (useSmoothed) {
            smoothed.map {
                MatchObservation(
                    ts = it.ts, lat = it.lat, lng = it.lng,
                    accuracyMeters = it.accuracyMeters,
                    bearingDeg = it.bearingDeg,
                    speedMps = it.speedMps,
                )
            }
        } else {
            committed.map { MatchObservation(ts = it.ts, lat = it.lat, lng = it.lng) }
        }
        if (observations.size < 2) return null

        val coords = observations.map { it.lat to it.lng }
        runCatching { osmRoadCache.ensureCorridorLoaded(coords) }
        val graph = osmRoadCache.corridorGraph(coords)

        val matched = MapMatcher(graph).match(observations)

        // Light curvature-/gap-aware finishing pass to de-jitter off-road straight runs (on-road
        // matched points are left exactly on the road centreline by the smoother).
        val finishedMatched = if (matched.size >= 3) TrackSmoother.smooth(matched) else matched
        val finished = finishedMatched.map { RoutePoint(ts = it.ts, lat = it.lat, lng = it.lng) }
        routeFileRepository.replacePoints(routeId, finished)
        return computeDistanceMeters(finished)
    }

    /** True when the smoothed track spans the committed track's time range within tolerance. */
    private fun smoothedCoversCommitted(smoothed: List<SmoothedSample>, committed: List<RoutePoint>): Boolean {
        val tol = SMOOTHED_COVERAGE_TOLERANCE_MS
        return smoothed.first().ts <= committed.first().ts + tol &&
                smoothed.last().ts >= committed.last().ts - tol
    }

    private fun computeDistanceMeters(points: List<RoutePoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng)
        }
        return total
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val a = sinDLat * sinDLat + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinDLon * sinDLon
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
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
