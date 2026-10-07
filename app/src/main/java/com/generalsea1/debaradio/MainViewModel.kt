package com.generalsea1.debaradio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RadioRepository()
    private val favoritesStore = FavoritesStore(app)
    private val database = AppDatabase.get(app)
    private var searchJob: Job? = null

    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private val _favorites = MutableStateFlow(favoritesStore.getFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val recordings: StateFlow<List<RecordingEntity>> =
        database.recordingDao()
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                repository.fetchStations()
            }.onSuccess {
                _stations.value = it
            }.onFailure {
                _error.value = "تعذر تحميل دليل المحطات: " + (it.message ?: "خطأ غير معروف")
            }
            _loading.value = false
        }
    }

    fun search(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            refresh()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            _loading.value = true
            _error.value = null
            runCatching {
                repository.searchStations(normalized)
            }.onSuccess {
                _stations.value = it
            }.onFailure {
                val localFallback = _stations.value.filter { station ->
                    listOf(
                        station.name,
                        station.countryName,
                        station.city.orEmpty(),
                        station.language.orEmpty(),
                        station.category.orEmpty()
                    ).joinToString(" ").contains(normalized, ignoreCase = true)
                }
                if (localFallback.isNotEmpty()) {
                    _stations.value = localFallback
                } else {
                    _error.value = "تعذر البحث في دليل المحطات."
                }
            }
            _loading.value = false
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

    fun markPlayed(station: RadioStation) {
        viewModelScope.launch {
            repository.markRadioBrowserClick(station.id)
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            database.recordingDao().delete(recording)
            java.io.File(recording.filePath).delete()
        }
    }
}
