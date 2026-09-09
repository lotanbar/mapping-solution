package com.mappingsolution.data.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingStateTest {

    @Test
    fun elapsedTimeFreezesAtStopTap() {
        val state = RecordingState.Active(
            routeId = "route",
            autoName = "route",
            startedAtMs = 1_000L,
            stoppingAtMs = 5_000L,
        )

        assertEquals(4_000L, state.elapsedMs(20_000L))
    }

    @Test
    fun stoppingWhilePausedDoesNotCountPausedTime() {
        val state = RecordingState.Active(
            routeId = "route",
            autoName = "route",
            startedAtMs = 1_000L,
            totalPausedMs = 1_000L,
            pausedSinceMs = 4_000L,
            stoppingAtMs = 5_000L,
        )

        assertEquals(2_000L, state.elapsedMs(20_000L))
    }
}
