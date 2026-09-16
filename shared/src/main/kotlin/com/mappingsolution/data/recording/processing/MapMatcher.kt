package com.mappingsolution.data.recording.processing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One GPS observation fed to the map-matcher (already Kalman-smoothed upstream). */
data class MatchObservation(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Double = HmmModel.DEFAULT_ACCURACY_M,
    val bearingDeg: Double? = null,
    val speedMps: Double = 0.0,
)

/** One matched output point. [onRoad] is false for passthrough points (off-road / no candidate). */
data class MatchedPoint(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val onRoad: Boolean,
)

/**
 * Shared HMM scoring model (Newson & Krumm, 2009) used by both the batch [MapMatcher] and the
 * streaming [OnlineMapMatcher], so live and final matching behave consistently.
 *
 * All scores are natural-log probabilities (higher = better); constants are dropped where they
 * don't affect the arg-max.
 */
object HmmModel {

    const val DEFAULT_ACCURACY_M = 12.0

    /**
     * GPS noise sigma clamps (metres). The lower clamp is speed-adaptive: tight at highway speed
     * (raw GPS is accurate and parallel carriageways must stay distinct) and looser in slow/city
     * driving (so the merely-nearest road no longer dominates — heading and topology get a say).
     */
    private const val MIN_SIGMA_HIGHWAY_M = 5.0
    private const val MIN_SIGMA_CITY_M = 8.0
    private const val MAX_SIGMA_M = 25.0

    /** Speed at or above which the tight (highway) sigma floor applies (m/s ≈ 72 km/h). */
    private const val HIGHWAY_SPEED_MPS = 20.0

    /** Transition scale β (metres): controls tolerance of |great-circle − on-road| distance. */
    const val BETA_M = 30.0

    /** Multiplier on the transition term so route plausibility competes with emission (snap distance)
     *  instead of being overwhelmed by it — the fix for greedy nearest-road snapping in cities. */
    private const val TRANSITION_LOG_WEIGHT = 1.5

    /** Penalty distance assigned when no on-road path is found (degraded, not impossible). */
    private const val DEGRADED_DIFF_M = 80.0

    /**
     * Soft bearing prior weight (log units), reached at [BEARING_FULL_SPEED_MPS]. Below
     * [BEARING_MIN_SPEED_MPS] the heading is too noisy to trust and the prior is off; between the
     * two it ramps linearly, so heading discriminates turns even in slow city traffic without
     * overtrusting jittery near-stationary fixes.
     */
    private const val BEARING_LOG_WEIGHT = 4.0
    private const val BEARING_MIN_SPEED_MPS = 1.0
    private const val BEARING_FULL_SPEED_MPS = 4.0

    /** Candidate search radius derived from accuracy, clamped (metres). */
    private const val MIN_RADIUS_M = 30.0
    private const val MAX_RADIUS_M = 80.0

    /** Max consecutive candidates kept per observation. */
    const val MAX_CANDIDATES = 6

    /** Beam width: prune Viterbi states whose log-score trails the best by more than this. */
    const val BEAM_LOG_MARGIN = 12.0

    /** Gap (ms) above which the trellis is severed (stationary suppression / GPS outage). */
    const val SEGMENT_BREAK_MS = 20_000L

    fun sigmaFor(accuracyMeters: Double, speedMps: Double = 0.0): Double {
        val floor = if (speedMps >= HIGHWAY_SPEED_MPS) MIN_SIGMA_HIGHWAY_M else MIN_SIGMA_CITY_M
        return (accuracyMeters / 2.0).coerceIn(floor, MAX_SIGMA_M)
    }

    fun radiusFor(accuracyMeters: Double): Double =
        (sigmaFor(accuracyMeters) * 4.0).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)

    /** Bearing-prior weight ramped by speed (0 below [BEARING_MIN_SPEED_MPS]). */
    private fun bearingWeight(speedMps: Double): Double = when {
        speedMps <= BEARING_MIN_SPEED_MPS -> 0.0
        speedMps >= BEARING_FULL_SPEED_MPS -> BEARING_LOG_WEIGHT
        else -> BEARING_LOG_WEIGHT *
            (speedMps - BEARING_MIN_SPEED_MPS) / (BEARING_FULL_SPEED_MPS - BEARING_MIN_SPEED_MPS)
    }

    /** Emission log-probability: Gaussian on the projection distance, plus a soft bearing prior. */
    fun emissionLog(obs: MatchObservation, cand: MatchCandidate): Double {
        val sigma = sigmaFor(obs.accuracyMeters, obs.speedMps)
        val z = cand.distMeters / sigma
        var log = -0.5 * z * z
        val bearing = obs.bearingDeg
        if (bearing != null) {
            val bw = bearingWeight(obs.speedMps)
            if (bw > 0.0) {
                val perp = GeoMath.bearingPerpendicularity(bearing, cand.segBearing) // [0,90]
                log -= bw * (perp / 90.0)
            }
        }
        return log
    }

    /** Maximum on-road distance worth searching for a transition given the straight-line gap. */
    fun transitionMaxDist(gcMeters: Double): Double =
        min(RoadGraph.NETWORK_SEARCH_CAP_M, gcMeters + max(60.0, gcMeters * 0.5))

    /**
     * Transition log-probability between consecutive candidates. Uses the on-road distance when
     * available; otherwise a fixed *degraded* penalty (missing connectivity must not hard-fail).
     */
    fun transitionLog(
        graph: RoadGraph,
        prev: MatchCandidate,
        next: MatchCandidate,
        gcMeters: Double,
    ): Double {
        val nd = graph.networkDistance(prev, next, transitionMaxDist(gcMeters))
        val diff = if (nd == null) DEGRADED_DIFF_M else abs(gcMeters - nd)
        return -TRANSITION_LOG_WEIGHT * diff / BETA_M
    }
}

/**
 * Offline (batch) HMM map-matcher. Runs the full Viterbi over an entire trajectory — used for the
 * high-quality pass when a recording is stopped.
 *
 * Observations with no candidate road (off-road) or separated by a long time gap break the
 * trellis: such points pass through unchanged and matching restarts cleanly afterwards.
 */
class MapMatcher(private val graph: RoadGraph) {

    fun match(observations: List<MatchObservation>): List<MatchedPoint> {
        if (observations.isEmpty()) return emptyList()
        val out = ArrayList<MatchedPoint>(observations.size)

        // Candidate set per observation (empty = off-road passthrough).
        val candsPerObs = observations.map { o ->
            graph.candidates(o.lat, o.lng, HmmModel.radiusFor(o.accuracyMeters), HmmModel.MAX_CANDIDATES)
        }

        var runStart = -1
        fun closeRun(endExclusive: Int) {
            if (runStart < 0) return
            viterbiRun(observations, candsPerObs, runStart, endExclusive, out)
            runStart = -1
        }

        for (i in observations.indices) {
            val cands = candsPerObs[i]
            val breakBefore = i > 0 &&
                observations[i].ts - observations[i - 1].ts > HmmModel.SEGMENT_BREAK_MS
            if (cands.isEmpty()) {
                closeRun(i)
                out.add(MatchedPoint(observations[i].ts, observations[i].lat, observations[i].lng, onRoad = false))
            } else {
                if (breakBefore) closeRun(i)
                if (runStart < 0) runStart = i
            }
        }
        closeRun(observations.size)
        return out
    }

    /** Viterbi over a contiguous run [start, end) where every observation has candidates. */
    private fun viterbiRun(
        obs: List<MatchObservation>,
        cands: List<List<MatchCandidate>>,
        start: Int,
        end: Int,
        out: MutableList<MatchedPoint>,
    ) {
        if (start >= end) return
        val n = end - start
        val score = ArrayList<DoubleArray>(n)
        val back = ArrayList<IntArray>(n)

        // Column 0.
        val c0 = cands[start]
        score.add(DoubleArray(c0.size) { HmmModel.emissionLog(obs[start], c0[it]) })
        back.add(IntArray(c0.size) { -1 })

        for (t in 1 until n) {
            val prevCands = cands[start + t - 1]
            val curCands = cands[start + t]
            val prevScore = score[t - 1]
            val gc = GeoMath.haversineMeters(
                obs[start + t - 1].lat, obs[start + t - 1].lng,
                obs[start + t].lat, obs[start + t].lng,
            )
            val bestPrev = prevScore.max()
            val curScore = DoubleArray(curCands.size) { Double.NEGATIVE_INFINITY }
            val curBack = IntArray(curCands.size) { 0 }
            for (j in curCands.indices) {
                var bj = Double.NEGATIVE_INFINITY
                var bi = 0
                for (i in prevCands.indices) {
                    // Beam prune: skip hopeless predecessors.
                    if (prevScore[i] < bestPrev - HmmModel.BEAM_LOG_MARGIN) continue
                    val tr = HmmModel.transitionLog(graph, prevCands[i], curCands[j], gc)
                    val s = prevScore[i] + tr
                    if (s > bj) { bj = s; bi = i }
                }
                curScore[j] = bj + HmmModel.emissionLog(obs[start + t], curCands[j])
                curBack[j] = bi
            }
            score.add(curScore)
            back.add(curBack)
        }

        // Backtrace.
        var k = indexOfMax(score[n - 1])
        val pickedLat = DoubleArray(n)
        val pickedLng = DoubleArray(n)
        for (t in n - 1 downTo 0) {
            val cand = cands[start + t][k]
            pickedLat[t] = cand.lat
            pickedLng[t] = cand.lng
            k = if (t > 0) back[t][k] else -1
        }
        for (t in 0 until n) {
            out.add(MatchedPoint(obs[start + t].ts, pickedLat[t], pickedLng[t], onRoad = true))
        }
    }

    private fun indexOfMax(a: DoubleArray): Int {
        var bi = 0; var bv = Double.NEGATIVE_INFINITY
        for (i in a.indices) if (a[i] > bv) { bv = a[i]; bi = i }
        return bi
    }
}
