package com.mappingsolution.desktop

import com.mappingsolution.data.util.KeyValueStore
import java.util.prefs.Preferences

/** [KeyValueStore] backed by java.util.prefs (Windows registry / Linux ~/.java user prefs). */
internal class PreferencesStore(private val node: Preferences) : KeyValueStore {
    override fun contains(key: String): Boolean = node.get(key, null) != null
    override fun getBoolean(key: String, default: Boolean): Boolean = node.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = node.putBoolean(key, value)
    override fun getString(key: String, default: String?): String? = node.get(key, default)
    override fun putString(key: String, value: String) = node.put(key, value)
    override fun getLong(key: String, default: Long): Long = node.getLong(key, default)
    override fun putLongs(values: Map<String, Long>) = values.forEach { (key, value) -> node.putLong(key, value) }

    companion object {
        val factory = KeyValueStore.Factory { name ->
            PreferencesStore(Preferences.userRoot().node("com/mappingsolution/$name"))
        }
    }
}
