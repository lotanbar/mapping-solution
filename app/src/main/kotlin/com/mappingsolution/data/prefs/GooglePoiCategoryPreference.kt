package com.mappingsolution.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePoiCategoryPreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("google_poi_categories", Context.MODE_PRIVATE)

    private val _showDiscovery = MutableStateFlow(prefs.getBoolean(KEY_DISCOVERY, true))
    val showDiscovery: StateFlow<Boolean> = _showDiscovery.asStateFlow()

    private val _showOther = MutableStateFlow(prefs.getBoolean(KEY_OTHER, false))
    val showOther: StateFlow<Boolean> = _showOther.asStateFlow()

    fun setShowDiscovery(value: Boolean) {
        _showDiscovery.value = value
        prefs.edit().putBoolean(KEY_DISCOVERY, value).apply()
    }

    fun setShowOther(value: Boolean) {
        _showOther.value = value
        prefs.edit().putBoolean(KEY_OTHER, value).apply()
    }

    fun modeKey(): String = "d${if (_showDiscovery.value) 1 else 0}o${if (_showOther.value) 1 else 0}"

    private companion object {
        const val KEY_DISCOVERY = "show_discovery"
        const val KEY_OTHER = "show_other"
    }
}
