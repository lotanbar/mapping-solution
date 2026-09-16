package com.mappingsolution.data.recording

sealed class RecordingEvent {
    data class Stopped(val routeId: String) : RecordingEvent()
}
