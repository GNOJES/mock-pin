package com.gnojes.mockpin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gnojes.mockpin.data.LocationStore
import com.gnojes.mockpin.data.SavedLocation
import com.gnojes.mockpin.location.*
import com.gnojes.mockpin.search.PlaceSuggestion
import com.gnojes.mockpin.search.PlacesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SearchState(
    val query: String = "",
    val results: List<PlaceSuggestion> = emptyList(),
    val loading: Boolean = false,
    val error: UserError? = null,
    val searched: Boolean = false,
)

class MockPinViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val store = LocationStore(application)
    private val places = PlacesRepository(application)
    val keyConfigured = places.keyConfigured
    private val initial = SavedLocation(
        savedState["selectedName"] ?: SavedLocation.DEFAULT.name,
        savedState["selectedLat"] ?: SavedLocation.DEFAULT.latitude,
        savedState["selectedLon"] ?: SavedLocation.DEFAULT.longitude,
        savedState["selectedAddress"], savedState["selectedId"],
    )
    private val selectedMutable = MutableStateFlow(initial.takeIf { it.valid } ?: SavedLocation.DEFAULT)
    val selected = selectedMutable.asStateFlow()
    val status = MockServiceState.status
    private val errorMutable = MutableStateFlow<UserError?>(null)
    val error = errorMutable.asStateFlow()
    private val searchMutable = MutableStateFlow(SearchState(query = savedState["query"] ?: ""))
    val search = searchMutable.asStateFlow()
    private var searchJob: Job? = null
    private var lastQuery: String? = null

    val favorites = store.favorites.catch { emit(emptyList()); reportStorageError() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = store.recent.catch { emit(emptyList()); reportStorageError() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (!status.value.running && !status.value.busy && MockLocationService.hasPendingCleanup(application)) {
            MockServiceState.publish(MockStatus(MockPhase.RECOVERY_REQUIRED, error = UserError(
                "이전 실행의 Mock Mode 정리를 확인할 수 없습니다. 위치 권한과 모의 위치 앱 설정을 확인한 뒤 정리해 주세요.", HelpAction.DEVELOPER_SETTINGS)))
        }
        if (search.value.query.isNotBlank()) queryChanged(search.value.query, force = true)
    }

    fun select(point: SavedLocation) {
        if (!point.valid) { report(UserError("위도는 -90~90, 경도는 -180~180이어야 합니다.")); return }
        searchJob?.cancel(); places.endSession(); lastQuery = null
        selectedMutable.value = point
        savedState["selectedName"] = point.name; savedState["selectedLat"] = point.latitude; savedState["selectedLon"] = point.longitude
        savedState["selectedAddress"] = point.address; savedState["selectedId"] = point.placeId
        savedState["query"] = ""
        searchMutable.value = SearchState()
        errorMutable.value = null
    }

    fun queryChanged(query: String, force: Boolean = false) {
        val normalized = query.trim()
        searchMutable.update { it.copy(query = query) }
        savedState["query"] = query
        if (!force && normalized == lastQuery) return
        searchJob?.cancel(); lastQuery = normalized
        searchMutable.value = SearchState(query)
        if (normalized.length < 2) { places.endSession(); return }
        searchJob = viewModelScope.launch {
            delay(350)
            searchMutable.update { it.copy(loading = true) }
            try {
                val predictions = places.autocomplete(normalized)
                ensureActive()
                searchMutable.update { it.copy(results = predictions, loading = false, searched = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                searchMutable.update { it.copy(loading = false, error = places.error(error)) }
            }
        }
    }

    fun retrySearch() { lastQuery = null; queryChanged(search.value.query, force = true) }
    fun chooseSuggestion(suggestion: PlaceSuggestion) {
        searchJob?.cancel()
        searchMutable.update { it.copy(loading = true, results = emptyList(), error = null) }
        searchJob = viewModelScope.launch {
            try {
                val point = places.details(suggestion)
                ensureActive()
                select(point)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                searchMutable.update { it.copy(loading = false, error = places.error(error)) }
            }
        }
    }

    fun saveFavorite(name: String) = viewModelScope.launch {
        try { store.addFavorite(selected.value, name) }
        catch (error: Exception) { if (error is CancellationException) throw error; reportStorageError() }
    }
    fun removeFavorite(point: SavedLocation) = viewModelScope.launch {
        try { store.removeFavorite(point) }
        catch (error: Exception) { if (error is CancellationException) throw error; reportStorageError() }
    }
    fun report(error: UserError) { errorMutable.value = error }
    fun dismissError() { errorMutable.value = null }
    private fun reportStorageError() { report(UserError("저장 위치를 읽거나 저장하지 못했습니다. 앱 저장 공간을 확인해 주세요.")) }
}
