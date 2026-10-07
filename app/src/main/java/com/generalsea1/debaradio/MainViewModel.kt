package com.generalsea1.debaradio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RadioRepository()
    private val favoritesStore = FavoritesStore(app)

    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private val _favorites = MutableStateFlow(favoritesStore.getFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _stations.value = repository.fetchStations()
            } catch (t: Throwable) {
                _error.value = "تعذر تحميل دليل المحطات: " + (t.message ?: "خطأ غير معروف")
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleFavorite(station: RadioStation) {
        val next = !_favorites.value.contains(station.id)
        favoritesStore.setFavorite(station.id, next)
        _favorites.value = favoritesStore.getFavorites()
    }

    fun saveLastStation(station: RadioStation) {
        favoritesStore.setLastStationId(station.id)
    }
}
