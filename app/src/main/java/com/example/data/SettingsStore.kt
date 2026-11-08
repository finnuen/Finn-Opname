package com.example.data

import android.content.Context

/**
 * Small, persistent app settings. SharedPreferences rather than DataStore because there is one
 * boolean here and it has to be readable before the first frame, not after a coroutine starts.
 */
class SettingsStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("finn_opname_settings", Context.MODE_PRIVATE)

    /** Dark mode is off on a first run. */
    var isDarkMode: Boolean
        get() = preferences.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    private companion object {
        const val KEY_DARK_MODE = "dark_mode"
    }
}
