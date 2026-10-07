package com.generalsea1.tmfm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RadioRepository()
    private val favoritesStore = FavoritesStore(app)
    private val recentStore = RecentStore(app)
    private val database = AppDatabase.get(app)
    private var job: Job? = null

    private val _stations = MutableStateFlow(
        RadioCatalogPolicy.filter(BundledCatalog.egypt + BundledCatalog.globalBaseline)
    )
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private val _countries = MutableStateFlow<List<RadioCountry>>(emptyList())
    val countries: StateFlow<List<RadioCountry>> = _countries.asStateFlow()

    private val _favorites = MutableStateFlow(favoritesStore.getFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(recentStore.ids())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _selectedCountry = MutableStateFlow("EG")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val recordings: StateFlow<List<RecordingEntity>> =
        database.recordingDao().observeAll()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    init {
        loadCountry("EG")
    }

    fun loadCountry(code: String) {
        val normalized = code.trim().uppercase()
        _selectedCountry.value = normalized
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { repository.fetchCountryStations(normalized) }
                .onSuccess { _stations.value = it }
                .onFailure {
                    val fallback = if (normalized == "EG") {
                        RadioCatalogPolicy.filter(BundledCatalog.egypt)
                    } else {
                        RadioCatalogPolicy.filter(BundledCatalog.globalBaseline.filter { it.countryCode == normalized })
                    }
                    _stations.value = fallback
                    _error.value = if (fallback.isEmpty()) {
                        "لا توجد بيانات محلية لهذه الدولة حاليًا."
                    } else {
                        "تعذر تحديث الدليل الخارجي؛ عُرضت البيانات المحلية."
                    }
                }
            _loading.value = false
        }
    }

    fun loadCountries() {
        viewModelScope.launch {
            runCatching { repository.fetchCountries() }
                .onSuccess { _countries.value = it }
                .onFailure { _error.value = "تعذر تحميل قائمة الدول الآن." }
        }
    }

    fun search(query: String) {
        val normalized = query.trim()
        job?.cancel()

        if (normalized.isBlank()) {
            loadCountry(_selectedCountry.value)
            return
        }

        job = viewModelScope.launch {
            delay(250)
            _loading.value = true
            _error.value = null
            runCatching { repository.searchStations(normalized) }
                .onSuccess { _stations.value = it }
                .onFailure {
                    _stations.value = _stations.value.filter { station ->
                        val text = listOfNotNull(
                            station.name,
                            station.nameArabic,
                            station.nameEnglish,
                            station.countryName,
                            station.city,
                            station.frequencyMhz?.toString(),
                            station.language,
                            station.category
                        ).joinToString(" ")
                        RadioCatalogPolicy.allow(station) &&
                            text.contains(normalized, ignoreCase = true)
                    }
                    if (_stations.value.isEmpty()) {
                        _error.value = "لا توجد محطة مطابقة."
                    }
                }
            _loading.value = false
        }
    }

    fun toggleFavorite(station: RadioStation) {
        val next = !favorites.value.contains(station.id)
        favoritesStore.setFavorite(station.id, next)
        _favorites.value = favoritesStore.getFavorites()
    }

    fun markPlayed(station: RadioStation) {
        favoritesStore.setLastStationId(station.id)
        recentStore.add(station.id)
        _recents.value = recentStore.ids()
        viewModelScope.launch {
            repository.markRadioBrowserClick(station.id)
        }
    }

    fun renameRecording(recording: RecordingEntity, title: String) {
        val normalized = title.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            database.recordingDao().rename(recording.id, normalized)
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            database.recordingDao().delete(recording)
            File(recording.filePath).delete()
        }
    }

    fun clearRecent() {
        recentStore.clear()
        _recents.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }
}
