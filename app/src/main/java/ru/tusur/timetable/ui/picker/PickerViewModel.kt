package ru.tusur.timetable.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.toTimetableError
import ru.tusur.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

data class PickerUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchItem> = emptyList(),
    val error: TimetableError? = null,
    val showMinLengthHint: Boolean = false,
)

/**
 * Ввод и поиск разделены намеренно: `query` обновляется синхронно на каждый символ
 * (поле ввода управляемое), а debounce задерживает только сетевой запрос.
 * Иначе введённый символ затирался бы предыдущим состоянием.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class PickerViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state
                .map { it.query }
                .distinctUntilChanged()
                .collectLatest { raw ->
                    searchJob?.cancel()
                    runSearch(raw.trim())
                }
        }
    }

    val saved: StateFlow<SourceSelection?> = prefs.selection.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    val recent: StateFlow<List<SearchItem>> = flow { emit(repository.recentSearches(RECENT_LIMIT)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onQueryChange(value: String) {
        // Немедленно: иначе TextField откатит ввод к старому значению.
        val meaningful = value.trim().length >= MIN_QUERY_LENGTH
        _state.update {
            it.copy(
                query = value,
                loading = meaningful,
                // Пробелы — не «слишком короткий запрос», а пустое поле.
                showMinLengthHint = value.isNotBlank() && !meaningful,
                error = null,
                results = if (meaningful) it.results else emptyList(),
            )
        }
    }

    /** Пикер открыли заново: старый запрос и результаты не должны «прилипать». */
    fun reset() {
        searchJob?.cancel()
        _state.value = PickerUiState()
    }

    private suspend fun runSearch(query: String) {
        if (query.length < MIN_QUERY_LENGTH) {
            _state.update {
                it.copy(
                    loading = false,
                    results = emptyList(),
                    error = null,
                    showMinLengthHint = it.query.isNotBlank() && it.query.trim().length < MIN_QUERY_LENGTH,
                )
            }
            return
        }

        delay(DEBOUNCE_MS)

        val result = repository.search(query)
        _state.update { current ->
            // Пока шёл запрос, пользователь мог изменить строку — тогда результат не нужен.
            if (current.query.trim() != query) {
                current
            } else {
                current.copy(
                    loading = false,
                    results = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull()?.toTimetableError(),
                )
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 220L
        const val MIN_QUERY_LENGTH = 2
        const val RECENT_LIMIT = 4
    }
}
