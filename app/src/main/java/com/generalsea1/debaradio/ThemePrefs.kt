package com.generalsea1.tmfm

import android.content.Context

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

object ThemePrefs {
    private const val PREFS = "tmfm_ui"
    private const val KEY = "theme"

    fun get(context: Context): ThemeChoice = runCatching {
        ThemeChoice.valueOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, ThemeChoice.SYSTEM.name) ?: ThemeChoice.SYSTEM.name
        )
    }.getOrDefault(ThemeChoice.SYSTEM)

    fun set(context: Context, value: ThemeChoice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value.name).apply()
    }
}
