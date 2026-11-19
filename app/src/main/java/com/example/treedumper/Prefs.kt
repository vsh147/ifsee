package com.example.treedumper

import android.content.Context

class Prefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var targetPackage: String
        get() = prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE) ?: DEFAULT_TARGET_PACKAGE
        set(value) {
            prefs.edit().putString(KEY_TARGET_PACKAGE, value).apply()
        }

    var dumpEnabled: Boolean
        get() = prefs.getBoolean(KEY_DUMP_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DUMP_ENABLED, value).apply()
        }

    var logFilter: String
        get() = prefs.getString(KEY_LOG_FILTER, DEFAULT_LOG_FILTER) ?: DEFAULT_LOG_FILTER
        set(value) {
            prefs.edit().putString(KEY_LOG_FILTER, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "treedumper_prefs"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_DUMP_ENABLED = "dump_enabled"
        const val KEY_LOG_FILTER = "log_filter"
        const val DEFAULT_TARGET_PACKAGE = "ru.yandex.taximeter"
        const val DEFAULT_LOG_FILTER = "Принять"
    }
}
