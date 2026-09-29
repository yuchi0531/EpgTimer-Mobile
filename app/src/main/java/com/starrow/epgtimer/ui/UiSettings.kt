package com.starrow.epgtimer.ui

import android.content.Context

object UiSettings {
    const val PREFS_NAME = "ui_settings"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_GUIDE_HOUR_HEIGHT = "guide_hour_height_dp"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val DEFAULT_GUIDE_HOUR_HEIGHT = 120
    const val MIN_GUIDE_HOUR_HEIGHT = 60
    const val MAX_GUIDE_HOUR_HEIGHT = 300

    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun guideHourHeight(context: Context): Int =
        prefs(context).getInt(KEY_GUIDE_HOUR_HEIGHT, DEFAULT_GUIDE_HOUR_HEIGHT)
            .coerceIn(MIN_GUIDE_HOUR_HEIGHT, MAX_GUIDE_HOUR_HEIGHT)

    fun setGuideHourHeight(context: Context, heightDp: Int) {
        prefs(context).edit()
            .putInt(KEY_GUIDE_HOUR_HEIGHT, heightDp.coerceIn(MIN_GUIDE_HOUR_HEIGHT, MAX_GUIDE_HOUR_HEIGHT))
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
