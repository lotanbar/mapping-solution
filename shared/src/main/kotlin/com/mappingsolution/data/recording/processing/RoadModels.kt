package com.mappingsolution.data.recording.processing

/**
 * A single OSM way (road/path) with geometry, connectivity, and metadata.
 *
 * [points] are the way's vertices as (lat, lng). [nodeIds] are the OSM node IDs of those
 * vertices, parallel to [points] (same size). The node IDs are what make the road network
 * *routable*: ways that meet at a junction share the same node ID, so a graph keyed by node
 * ID reconstructs the true topology used by [RoadGraph] for map-matching.
 *
 * [id] is the OSM way ID, used for deduplication across overlapping tile fetches.
 *
 * Pure data — no Android dependencies — so the map-matching core is JVM unit-testable.
 */
data class OsmRoadWay(
    val id: Long,
    val highway: String,
    val name: String?,
    val points: List<Pair<Double, Double>>,
    val nodeIds: List<Long> = emptyList(),
    /** Travel direction along [points] order: 0 = bidirectional, +1 = forward only (A→B), -1 = backward only. */
    val oneway: Int = 0,
)
