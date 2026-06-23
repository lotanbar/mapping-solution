package com.mappingsolution.data.recording.processing

import java.util.PriorityQueue
import kotlin.math.floor
import kotlin.math.max

/**
 * A candidate map-match for one GPS observation: a projection of the observation onto a
 * specific road segment.
 *
 * @param segId       Index of the matched segment in [RoadGraph.segments].
 * @param lat,lng     Foot of the projection (the snapped position for this candidate).
 * @param t           Fraction along the segment's A→B direction of the foot point.
 * @param distMeters  Distance from the observation to the foot point (drives emission probability).
 * @param highway     OSM highway class of the segment.
 * @param segBearing  Compass bearing of the segment (for the bearing-compatibility prior).
 */
data class MatchCandidate(
    val segId: Int,
    val lat: Double,
    val lng: Double,
    val t: Double,
    val distMeters: Double,
    val highway: String,
    val segBearing: Double,
)

/**
 * A routable road network built from cached OSM ways.
 *
 * Two responsibilities:
 *  1. **Candidate generation** — given a GPS observation, return the nearest road-segment
 *     projections within a radius (spatial-grid accelerated).
 *  2. **Network distance** — the shortest on-road distance between two candidate projections,
 *     used as the HMM transition feature. Routing is a capped Dijkstra over the node graph,
 *     bounded by [NETWORK_SEARCH_CAP_M] with per-source memoisation.
 *
 * The graph is **undirected** (one-way restrictions are ignored): for the short gaps between
 * consecutive GPS fixes this is a safe simplification and avoids brittle behaviour on
 * incompletely-tagged data.
 *
 * Pure Kotlin — JVM unit-testable, no Android dependencies.
 */
class RoadGraph private constructor(
    val segments: List<Segment>,
    private val adjacency: Map<Long, List<Edge>>,
    private val nodeLat: Map<Long, Double>,
    private val nodeLng: Map<Long, Double>,
    private val grid: Map<Long, IntArray>,
    private val cellSizeDeg: Double,
) {

    /** A directed half of an undirected road edge between two OSM nodes. */
    class Edge(val to: Long, val lengthM: Double)

    /** One straight piece of a road way, between two consecutive OSM nodes. */
    class Segment(
        val aNode: Long,
        val bNode: Long,
        val aLat: Double, val aLng: Double,
        val bLat: Double, val bLng: Double,
        val lengthM: Double,
        val highway: String,
        val bearing: Double,
        /** Travel direction A→B: 0 = both ways, +1 = forward only, -1 = backward only. */
        val oneway: Int = 0,
        /** Soft penalty multiplier applied to distance when travelling against [oneway]. */
        val reverseMul: Double = 1.0,
    )

    val isEmpty: Boolean get() = segments.isEmpty()

    // ── Candidate generation ──────────────────────────────────────────────────────────────────

    /**
     * Returns up to [maxK] candidate projections within [radiusM] of the observation,
     * nearest first. Empty when the observation is off-road (no segment within radius).
     */
    fun candidates(lat: Double, lng: Double, radiusM: Double, maxK: Int): List<MatchCandidate> {
        if (segments.isEmpty()) return emptyList()
        // Search the grid cells overlapping a radiusM box around the point.
        val cellsLat = max(1, floor(radiusM / (cellSizeDeg * 111_320.0)).toInt() + 1)
        val cLat = floor(lat / cellSizeDeg).toInt()
        val cLng = floor(lng / cellSizeDeg).toInt()

        val seen = HashSet<Int>()
        val found = ArrayList<MatchCandidate>()
        for (dLat in -cellsLat..cellsLat) {
            for (dLng in -cellsLat..cellsLat) {
                val key = cellKey(cLat + dLat, cLng + dLng)
                val segIdxs = grid[key] ?: continue
                for (segId in segIdxs) {
                    if (!seen.add(segId)) continue
                    val s = segments[segId]
                    val proj = GeoMath.projectOnSegment(lat, lng, s.aLat, s.aLng, s.bLat, s.bLng)
                    if (proj.distMeters <= radiusM) {
                        found.add(
                            MatchCandidate(
                                segId = segId,
                                lat = proj.lat, lng = proj.lng, t = proj.t,
                                distMeters = proj.distMeters,
                                highway = s.highway, segBearing = s.bearing,
                            )
                        )
                    }
                }
            }
        }
        found.sortBy { it.distMeters }
        return if (found.size > maxK) found.subList(0, maxK).toList() else found
    }

    // ── Network distance ──────────────────────────────────────────────────────────────────────

    /** Per-source shortest-distance maps, capped at [NETWORK_SEARCH_CAP_M]. Bounded LRU. */
    private val dijkstraCache = object : LinkedHashMap<Long, Map<Long, Double>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Map<Long, Double>>): Boolean =
            size > DIJKSTRA_CACHE_MAX
    }

    /**
     * Shortest on-road distance between two candidate projections, or `null` when no path is
     * found within [maxDistM] (disconnected, or beyond the cap). Callers should treat `null`
     * as a *degraded* (penalised) transition rather than an impossibility — the live graph can
     * legitimately be missing connectivity near tile edges.
     */
    fun networkDistance(a: MatchCandidate, b: MatchCandidate, maxDistM: Double): Double? {
        if (a.segId == b.segId) {
            val seg = segments[a.segId]
            return segDirCost(seg, kotlin.math.abs(a.t - b.t) * seg.lengthM, towardB = b.t >= a.t)
        }
        val sa = segments[a.segId]
        val sb = segments[b.segId]
        // Partial-segment costs from each projection to its two endpoint nodes — direction-aware so
        // travelling the wrong way down a one-way segment is penalised, not free.
        val aEnds = listOf(
            sa.aNode to segDirCost(sa, a.t * sa.lengthM, towardB = false),
            sa.bNode to segDirCost(sa, (1.0 - a.t) * sa.lengthM, towardB = true),
        )
        val bEnds = listOf(
            sb.aNode to segDirCost(sb, b.t * sb.lengthM, towardB = true),
            sb.bNode to segDirCost(sb, (1.0 - b.t) * sb.lengthM, towardB = false),
        )

        var best = Double.MAX_VALUE
        for ((srcNode, srcCost) in aEnds) {
            if (srcCost >= best) continue
            val distMap = dijkstraFrom(srcNode)
            for ((dstNode, dstCost) in bEnds) {
                val mid = distMap[dstNode] ?: continue
                val total = srcCost + mid + dstCost
                if (total < best) best = total
            }
        }
        return if (best <= maxDistM) best else null
    }

    /** Single-source Dijkstra capped at [NETWORK_SEARCH_CAP_M], memoised per source node. */
    private fun dijkstraFrom(src: Long): Map<Long, Double> {
        dijkstraCache[src]?.let { return it }
        val dist = HashMap<Long, Double>()
        if (!adjacency.containsKey(src)) {
            dijkstraCache[src] = dist
            return dist
        }
        val pq = PriorityQueue<LongDouble>(compareBy { it.d })
        dist[src] = 0.0
        pq.add(LongDouble(src, 0.0))
        while (pq.isNotEmpty()) {
            val cur = pq.poll()
            if (cur.d > (dist[cur.n] ?: Double.MAX_VALUE)) continue
            if (cur.d > NETWORK_SEARCH_CAP_M) break
            val edges = adjacency[cur.n] ?: continue
            for (e in edges) {
                val nd = cur.d + e.lengthM
                if (nd > NETWORK_SEARCH_CAP_M) continue
                if (nd < (dist[e.to] ?: Double.MAX_VALUE)) {
                    dist[e.to] = nd
                    pq.add(LongDouble(e.to, nd))
                }
            }
        }
        dijkstraCache[src] = dist
        return dist
    }

    private class LongDouble(val n: Long, val d: Double)

    /**
     * Distance cost of travelling [dist] metres along [seg] in the given direction, applying the
     * soft one-way penalty when moving against the segment's allowed direction. [towardB] is true
     * when travelling in the segment's A→B sense.
     */
    private fun segDirCost(seg: Segment, dist: Double, towardB: Boolean): Double {
        val againstOneway = (towardB && seg.oneway == -1) || (!towardB && seg.oneway == 1)
        return if (againstOneway) dist * seg.reverseMul else dist
    }

    companion object {
        /** Maximum on-road distance Dijkstra will explore from any source (metres). */
        const val NETWORK_SEARCH_CAP_M = 2_000.0

        /**
         * Soft wrong-way penalty multipliers (distance is scaled by these against a one-way).
         *
         * Scaled by road class. On small roads, one-way couplets are drawn only a few metres
         * apart and GPS cannot resolve which carriageway you are on, so a wrong-carriageway match
         * is harmless and invisible — a strong penalty there causes the match to dart onto the far
         * parallel line. On grade-separated roads carriageways are physically far apart and a
         * wrong-way match is genuinely implausible, so the penalty stays strong there.
         */
        private const val ONEWAY_REVERSE_PENALTY_LOCAL = 1.5
        private const val ONEWAY_REVERSE_PENALTY_ARTERIAL = 3.0
        private const val ONEWAY_REVERSE_PENALTY_FAST = 8.0

        /** Wrong-way penalty scaled by road class (see [ONEWAY_REVERSE_PENALTY_LOCAL]). */
        private fun reversePenaltyFor(highway: String): Double = when (highway) {
            "motorway", "motorway_link", "trunk", "trunk_link" -> ONEWAY_REVERSE_PENALTY_FAST
            "primary", "primary_link", "secondary", "secondary_link" -> ONEWAY_REVERSE_PENALTY_ARTERIAL
            else -> ONEWAY_REVERSE_PENALTY_LOCAL
        }

        /** Maximum number of cached per-source distance maps. */
        private const val DIJKSTRA_CACHE_MAX = 256

        private fun cellKey(cLat: Int, cLng: Int): Long =
            (cLat.toLong() shl 32) xor (cLng.toLong() and 0xffffffffL)

        /**
         * Builds a [RoadGraph] from OSM ways. Ways lacking parallel node IDs are still usable
         * for candidate generation but contribute no routable edges (so transitions across them
         * fall back to the degraded model). Spatial grid cells are ~[cellSizeDeg] across.
         */
        fun build(ways: List<OsmRoadWay>, cellSizeDeg: Double = 0.002): RoadGraph {
            val segments = ArrayList<Segment>()
            val adj = HashMap<Long, ArrayList<Edge>>()
            val nLat = HashMap<Long, Double>()
            val nLng = HashMap<Long, Double>()
            val gridBuild = HashMap<Long, ArrayList<Int>>()

            // Synthetic node IDs for ways without OSM node IDs (negative to avoid collisions).
            var synthetic = -1L

            for (way in ways) {
                val pts = way.points
                if (pts.size < 2) continue
                val hasNodes = way.nodeIds.size == pts.size
                val reverseMul = reversePenaltyFor(way.highway)
                for (i in 0 until pts.size - 1) {
                    val (aLat, aLng) = pts[i]
                    val (bLat, bLng) = pts[i + 1]
                    val aNode = if (hasNodes) way.nodeIds[i] else synthetic--
                    val bNode = if (hasNodes) way.nodeIds[i + 1] else synthetic--
                    val len = GeoMath.haversineMeters(aLat, aLng, bLat, bLng)
                    if (len <= 0.0) continue
                    val segId = segments.size
                    segments.add(
                        Segment(
                            aNode = aNode, bNode = bNode,
                            aLat = aLat, aLng = aLng, bLat = bLat, bLng = bLng,
                            lengthM = len, highway = way.highway,
                            bearing = GeoMath.bearing(aLat, aLng, bLat, bLng),
                            oneway = way.oneway, reverseMul = reverseMul,
                        )
                    )
                    nLat[aNode] = aLat; nLng[aNode] = aLng
                    nLat[bNode] = bLat; nLng[bNode] = bLng
                    if (hasNodes) {
                        // Keep both directions connected (robust to mistagged data), but penalise the
                        // wrong-way edge of a one-way segment with a soft multiplier.
                        val fwdMul = if (way.oneway == -1) reverseMul else 1.0  // A→B against a −1 way
                        val revMul = if (way.oneway == 1) reverseMul else 1.0   // B→A against a +1 way
                        adj.getOrPut(aNode) { ArrayList() }.add(Edge(bNode, len * fwdMul))
                        adj.getOrPut(bNode) { ArrayList() }.add(Edge(aNode, len * revMul))
                    }
                    // Index the segment into every grid cell its endpoints touch.
                    indexSegment(gridBuild, segId, aLat, aLng, bLat, bLng, cellSizeDeg)
                }
            }
            val grid = gridBuild.mapValues { it.value.toIntArray() }
            return RoadGraph(segments, adj, nLat, nLng, grid, cellSizeDeg)
        }

        private fun indexSegment(
            grid: HashMap<Long, ArrayList<Int>>,
            segId: Int,
            aLat: Double, aLng: Double, bLat: Double, bLng: Double,
            cellSizeDeg: Double,
        ) {
            val latLo = floor(minOf(aLat, bLat) / cellSizeDeg).toInt()
            val latHi = floor(maxOf(aLat, bLat) / cellSizeDeg).toInt()
            val lngLo = floor(minOf(aLng, bLng) / cellSizeDeg).toInt()
            val lngHi = floor(maxOf(aLng, bLng) / cellSizeDeg).toInt()
            for (cl in latLo..latHi) {
                for (cg in lngLo..lngHi) {
                    grid.getOrPut(cellKey(cl, cg)) { ArrayList() }.add(segId)
                }
            }
        }
    }
}
