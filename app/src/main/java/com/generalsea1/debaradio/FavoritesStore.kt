package com.generalsea1.tmfm

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("tmfm_radio", Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun setFavorite(id: String, favorite: Boolean) {
        val next = getFavorites().toMutableSet()
        if (favorite) next.add(id) else next.remove(id)
        prefs.edit().putStringSet("favorites", next).apply()
    }

    fun setLastStationId(id: String) {
        prefs.edit().putString("last_station", id).apply()
    }
}
