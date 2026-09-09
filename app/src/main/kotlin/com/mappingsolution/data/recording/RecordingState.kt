package com.mappingsolution.data.recording

data class RecordingPoint(val ts: Long, val lat: Double, val lng: Double)

/**
 * One raw Kalman-smoothed sample persisted to `smoothed.jsonl`, carrying the GPS observation
 * features the Stop map-match pass needs to score candidates well: the heading [bearingDeg] and
 * [speedMps] (the strongest cue for which road / which turn at a junction) and the per-fix
 * [accuracyMeters] (drives emission sigma). Defaults match the [MatchObservation] defaults so
 * older `smoothed.jsonl` files that stored only ts/lat/lng still read back sensibly.
 */
data class SmoothedSample(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val bearingDeg: Double? = null,
    val speedMps: Double = 0.0,
    val accuracyMeters: Double = 12.0,
)

sealed class RecordingState {
    object Idle : RecordingState()

    data class Active(
        val routeId: String,
        val autoName: String,
        val startedAtMs: Long,
        val totalPausedMs: Long = 0L,
        val pausedSinceMs: Long? = null,
        /** Set on the Stop tap so elapsed time freezes while final buffered writes complete. */
        val stoppingAtMs: Long? = null,
        val points: List<RecordingPoint> = emptyList(),
        /** Provisional live "tip" drawn ahead of [points] while the matcher waits to commit. Not persisted. */
        val liveHead: RecordingPoint? = null,
        val distanceMeters: Double = 0.0,
        val color: String = "#FFFF5722",
    ) : RecordingState() {
        val isPaused: Boolean get() = pausedSinceMs != null
        val isStopping: Boolean get() = stoppingAtMs != null

        fun elapsedMs(nowMs: Long): Long {
            val effectiveNow = stoppingAtMs ?: nowMs
            val extra = if (pausedSinceMs != null) effectiveNow - pausedSinceMs else 0L
            return (effectiveNow - startedAtMs - totalPausedMs - extra).coerceAtLeast(0L)
        }
    }
}
