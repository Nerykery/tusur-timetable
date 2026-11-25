package ru.tusur.timetable.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.BuildingMap
import ru.tusur.timetable.domain.model.MapRoom
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableException
import ru.tusur.timetable.domain.repository.BuildingRepository
import java.time.Clock
import javax.inject.Inject

data class BuildingMapUiState(
    val map: BuildingMap? = null,
    val load: BuildingLoad? = null,
    val loading: Boolean = true,
    val loadError: TimetableError? = null,
    val floor: Int = 1,
    val selectedRoomId: String? = null,
    val query: String = "",
    val results: List<MapRoom> = emptyList(),
    val dayIndex: Int = 0,
    val pairIndex: Int = 0,
    val autoSlot: Boolean = true,
) {
    val selectedRoom: MapRoom?
        get() = selectedRoomId?.let { id ->
            map?.roomsByFloor?.values?.flatten()?.firstOrNull { it.id == id }
        }
}

@HiltViewModel
class BuildingMapViewModel @Inject constructor(
    private val repository: BuildingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BuildingMapUiState())
    val state: StateFlow<BuildingMapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadMap() }
        viewModelScope.launch { loadOccupancy() }
    }

    fun selectFloor(floor: Int) {
        _state.update { it.copy(floor = floor) }
    }

    fun selectRoom(room: MapRoom) {
        _state.update {
            it.copy(selectedRoomId = room.id, floor = room.floor, query = "")
        }
    }

    fun selectRoomByCode(code: String) {
        val map = _state.value.map ?: return
        val room = map.find(code) ?: return
        selectRoom(room)
    }

    fun clearRoom() {
        _state.update { it.copy(selectedRoomId = null) }
    }

    fun onQueryChange(query: String) {
        val map = _state.value.map
        _state.update {
            it.copy(
                query = query,
                results = map?.search(query).orEmpty(),
            )
        }
    }

    fun selectDay(dayIndex: Int) {
        _state.update { it.copy(dayIndex = dayIndex, autoSlot = false) }
    }

    fun selectPair(pairIndex: Int) {
        _state.update { it.copy(pairIndex = pairIndex, autoSlot = false) }
    }

    fun followNow() {
        val load = _state.value.load ?: return
        applyCurrentSlot(load)
    }

    fun retry() {
        viewModelScope.launch { loadOccupancy() }
    }

    fun applyCurrentSlot(load: BuildingLoad) {
        val today = TomskTime.today(clock)
        val sameWeek = TomskTime.mondayOf(today) == load.monday
        val day = if (sameWeek) TomskTime.dayIndex(today).coerceIn(0, MAX_DAY_INDEX) else 0
        val slot = load.currentOrNextSlot(TomskTime.nowMinutes(clock))
        val pair = slot?.pairIndex ?: 0
        _state.update {
            it.copy(dayIndex = day, pairIndex = pair, autoSlot = true)
        }
    }

    private suspend fun loadMap() {
        val map = repository.map()
        _state.update {
            it.copy(
                map = map,
                floor = map.floors.firstOrNull { floor -> floor == 1 } ?: map.floors.firstOrNull() ?: 1,
                loading = false,
            )
        }
    }

    private suspend fun loadOccupancy() {
        _state.update { it.copy(loadError = null) }
        repository.load().fold(
            onSuccess = { load ->
                _state.update { it.copy(load = load) }
                if (_state.value.autoSlot) applyCurrentSlot(load)
            },
            onFailure = { throwable ->
                val error = (throwable as? TimetableException)?.error
                    ?: TimetableError(ru.tusur.timetable.domain.model.TimetableErrorKind.NETWORK)
                _state.update { it.copy(loadError = error) }
            },
        )
    }

    private companion object {
        const val MAX_DAY_INDEX = 5
    }
}
