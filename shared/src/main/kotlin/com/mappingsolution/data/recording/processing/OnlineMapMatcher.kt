package com.mappingsolution.data.recording.processing

/**
 * Streaming (online) HMM map-matcher with bounded lag.
 *
 * Runs an incremental Viterbi as observations arrive and **commits** a matched point only once
 * it is confident — either because every surviving path agrees on it (convergence) or because it
 * has fallen [maxLag] fixes behind the frontier (forced commit). This is what lets the live track
 * stay on the correct road through an intersection: the decision for "where was I a few seconds
 * ago" waits until the next fixes reveal where the user actually went.
 *
 * Committed points lag the current position by up to [maxLag] fixes; the caller shows the latest
 * smoothed position as a provisional "head" so the on-screen line still reaches the user.
 *
 * Live commits are provisional: the authoritative result is the full [MapMatcher] pass run when
 * recording stops. To keep the implementation bounded and simple, when the road graph changes
 * (new tiles) or a long time gap occurs, the current trellis is flushed and matching restarts.
 *
 * Not thread-safe; drive it from a single coroutine.
 */
class OnlineMapMatcher(private val maxLag: Int = DEFAULT_MAX_LAG) {

    private class Col(
        val obs: MatchObservation,
        val cands: List<MatchCandidate>,
        val score: DoubleArray,
        val back: IntArray, // index into previous column's candidate list; ignored for the front column
    )

    private val cols = ArrayList<Col>()
    private var graph: RoadGraph? = null
    private var lastObsTs: Long? = null

    fun reset() {
        cols.clear()
        graph = null
        lastObsTs = null
    }

    /**
     * Feed one observation against the [currentGraph] (the road network near the user right now).
     * Returns the points committed as a result — usually 0 or 1, occasionally more after a
     * convergence or a flush. Points are returned in chronological order.
     */
    fun add(obs: MatchObservation, currentGraph: RoadGraph): List<MatchedPoint> {
        val out = ArrayList<MatchedPoint>()

        // A graph swap invalidates segment indices in the live trellis — flush and restart.
        if (graph !== currentGraph) {
            out.addAll(flushInternal())
            graph = currentGraph
        }
        val g = currentGraph

        val dtBreak = lastObsTs?.let { obs.ts - it > HmmModel.SEGMENT_BREAK_MS } ?: false
        val cands = g.candidates(obs.lat, obs.lng, HmmModel.radiusFor(obs.accuracyMeters), HmmModel.MAX_CANDIDATES)
        lastObsTs = obs.ts

        if (cands.isEmpty()) {
            // Off-road: flush the matched segment so far, then pass this point through unchanged.
            out.addAll(flushInternal())
            out.add(MatchedPoint(obs.ts, obs.lat, obs.lng, onRoad = false))
            return out
        }
        if (dtBreak) out.addAll(flushInternal())

        if (cols.isEmpty()) {
            val score = DoubleArray(cands.size) { HmmModel.emissionLog(obs, cands[it]) }
            normalize(score)
            cols.add(Col(obs, cands, score, IntArray(cands.size) { -1 }))
        } else {
            val prev = cols.last()
            val gc = GeoMath.haversineMeters(prev.obs.lat, prev.obs.lng, obs.lat, obs.lng)
            val bestPrev = prev.score.max()
            val curScore = DoubleArray(cands.size) { Double.NEGATIVE_INFINITY }
            val curBack = IntArray(cands.size) { 0 }
            for (j in cands.indices) {
                var bj = Double.NEGATIVE_INFINITY
                var bi = 0
                for (i in prev.cands.indices) {
                    if (prev.score[i] < bestPrev - HmmModel.BEAM_LOG_MARGIN) continue
                    val s = prev.score[i] + HmmModel.transitionLog(g, prev.cands[i], cands[j], gc)
                    if (s > bj) { bj = s; bi = i }
                }
                curScore[j] = bj + HmmModel.emissionLog(obs, cands[j])
                curBack[j] = bi
            }
            normalize(curScore)
            cols.add(Col(obs, cands, curScore, curBack))
        }

        out.addAll(commit())
        return out
    }

    /** Force-commit the remaining window (call on stop/pause). */
    fun flush(): List<MatchedPoint> = flushInternal()

    // ── internals ─────────────────────────────────────────────────────────────────────────────

    private fun commit(): List<MatchedPoint> {
        val out = ArrayList<MatchedPoint>()
        // Convergence: while every surviving path agrees on the front column, commit it.
        while (cols.size > 1) {
            val ancestor = sharedFrontAncestor() ?: break
            out.add(point(cols[0], ancestor))
            cols.removeAt(0)
        }
        // Forced: keep the window within maxLag of the frontier.
        while (cols.size > maxLag + 1) {
            val ancestor = traceBackToFront()
            out.add(point(cols[0], ancestor))
            cols.removeAt(0)
        }
        return out
    }

    private fun flushInternal(): List<MatchedPoint> {
        if (cols.isEmpty()) return emptyList()
        val n = cols.size
        val picked = IntArray(n)
        var k = indexOfMax(cols[n - 1].score)
        for (c in n - 1 downTo 0) {
            picked[c] = k
            if (c > 0) k = cols[c].back[k]
        }
        val out = ArrayList<MatchedPoint>(n)
        for (c in 0 until n) out.add(point(cols[c], picked[c]))
        cols.clear()
        return out
    }

    /** Ancestor index at the front column shared by ALL terminal states, or null if they differ. */
    private fun sharedFrontAncestor(): Int? {
        val last = cols.size - 1
        var shared = -1
        for (s in cols[last].cands.indices) {
            var k = s
            for (c in last downTo 1) k = cols[c].back[k]
            if (shared == -1) shared = k else if (shared != k) return null
        }
        return shared
    }

    /** Ancestor index at the front column along the current best (max-score) terminal path. */
    private fun traceBackToFront(): Int {
        val last = cols.size - 1
        var k = indexOfMax(cols[last].score)
        for (c in last downTo 1) k = cols[c].back[k]
        return k
    }

    private fun point(col: Col, idx: Int): MatchedPoint =
        MatchedPoint(col.obs.ts, col.cands[idx].lat, col.cands[idx].lng, onRoad = true)

    private fun normalize(score: DoubleArray) {
        val m = score.max()
        if (m.isFinite()) for (i in score.indices) score[i] -= m
    }

    private fun indexOfMax(a: DoubleArray): Int {
        var bi = 0; var bv = Double.NEGATIVE_INFINITY
        for (i in a.indices) if (a[i] > bv) { bv = a[i]; bi = i }
        return bi
    }

    companion object {
        /** Default commit lag in fixes (~2 s each ⇒ ~16 s of lookahead at the tip). */
        const val DEFAULT_MAX_LAG = 8
    }
}
