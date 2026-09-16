package com.mappingsolution.data.util

/** Small persistent key-value settings store (Android: SharedPreferences; desktop: java.util.prefs). */
interface KeyValueStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, default: Long): Long
    fun putLongs(values: Map<String, Long>)

    /** Opens the store named [name]; each name is an independent namespace. */
    fun interface Factory {
        fun open(name: String): KeyValueStore
    }
}
