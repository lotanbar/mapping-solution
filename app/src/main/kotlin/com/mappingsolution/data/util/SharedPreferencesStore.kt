package com.mappingsolution.data.util

import android.content.SharedPreferences

class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun contains(key: String): Boolean = prefs.contains(key)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLongs(values: Map<String, Long>) {
        prefs.edit().apply { values.forEach { (key, value) -> putLong(key, value) } }.apply()
    }
}
