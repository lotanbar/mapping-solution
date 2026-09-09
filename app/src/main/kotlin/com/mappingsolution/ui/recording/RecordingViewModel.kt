package com.mappingsolution.ui.recording

import android.app.Application
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.mappingsolution.data.recording.RecordingEvent
import com.mappingsolution.data.recording.RecordingRepository
import com.mappingsolution.data.recording.RecordingState
import com.mappingsolution.service.RecordingService
import com.mappingsolution.service.RouteRefinementWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val recordingRepository: RecordingRepository,
) : AndroidViewModel(application) {

    val state: StateFlow<RecordingState> = recordingRepository.state
    val events: SharedFlow<RecordingEvent> = recordingRepository.events

    fun consumeStoppedEvent() = recordingRepository.consumeStoppedEvent()

    fun needsBatteryOptimizationExemption(): Boolean {
        val pm = getApplication<Application>().getSystemService(PowerManager::class.java)
        return !pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun startRecording() {
        ContextCompat.startForegroundService(getApplication(), RecordingService.startIntent(getApplication()))
    }

    fun pauseRecording() {
        getApplication<Application>().startService(RecordingService.pauseIntent(getApplication()))
    }

    fun resumeRecording() {
        getApplication<Application>().startService(RecordingService.resumeIntent(getApplication()))
    }

    fun resumeIncomplete(routeId: String) {
        ContextCompat.startForegroundService(getApplication(), RecordingService.resumeIncompleteIntent(getApplication(), routeId))
    }

    fun stopRecording() {
        getApplication<Application>().startService(RecordingService.stopIntent(getApplication()))
    }

    fun refineRoute(routeId: String) {
        RouteRefinementWorker.enqueue(getApplication(), routeId)
    }

    fun setRecordingColor(color: String) = recordingRepository.updateLiveColor(color)
}
