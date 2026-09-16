package com.mappingsolution.data.recording.processing

/** Hysteresis-based mode manager — prevents flickering at road/off-road transitions. */
enum class TrackMode { ROAD, OFF_ROAD }

class TrackModeManager {

    companion object {
        /** Consecutive snapped fixes required to enter ROAD mode. */
        const val ROAD_ENTER_COUNT = 3

        /** Consecutive unsnapped fixes required to exit to OFF_ROAD mode. */
        const val ROAD_EXIT_COUNT = 3
    }

    var mode: TrackMode = TrackMode.OFF_ROAD
        private set

    private var roadConsecutive = 0
    private var offRoadConsecutive = 0

    fun reset() {
        mode = TrackMode.OFF_ROAD
        roadConsecutive = 0
        offRoadConsecutive = 0
    }

    /**
     * Call after each GPS fix with whether a road snap was found.
     * Returns the (possibly updated) current [TrackMode].
     */
    fun onSnappedResult(snapped: Boolean): TrackMode {
        if (snapped) {
            roadConsecutive++
            offRoadConsecutive = 0
            if (mode == TrackMode.OFF_ROAD && roadConsecutive >= ROAD_ENTER_COUNT) {
                mode = TrackMode.ROAD
            }
        } else {
            offRoadConsecutive++
            roadConsecutive = 0
            if (mode == TrackMode.ROAD && offRoadConsecutive >= ROAD_EXIT_COUNT) {
                mode = TrackMode.OFF_ROAD
            }
        }
        return mode
    }
}
