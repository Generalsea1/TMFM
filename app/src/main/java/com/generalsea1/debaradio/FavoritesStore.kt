package com.generalsea1.debaradio

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("tmfm_radio", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("deba_radio", Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> =
        (prefs.getStringSet("favorites", emptySet()) ?: emptySet()) +
            (legacyPrefs.getStringSet("favorites", emptySet()) ?: emptySet())

    fun setFavorite(id: String, favorite: Boolean) {
        val next = getFavorites().toMutableSet()
        if (favorite) next.add(id) else next.remove(id)
        prefs.edit().putStringSet("favorites", next).apply()
    }

    fun setLastStationId(id: String) {
        prefs.edit().putString("last_station", id).apply()
    }

    fun getLastStationId(): String? =
        prefs.getString("last_station", null)
            ?: legacyPrefs.getString("last_station", null)
}
