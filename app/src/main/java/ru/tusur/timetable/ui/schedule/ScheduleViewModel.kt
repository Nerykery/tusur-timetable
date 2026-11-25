package ru.tusur.timetable.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.data.repository.RefreshCoordinator
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.domain.model.TimetableException
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.repository.TimetableRepository
import ru.tusur.timetable.ui.navigation.AppLink
import ru.tusur.timetable.ui.navigation.DeepLinkBus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val sourceKey: String? = null,
    val sourceLabel: String = "",
    val monday: LocalDate,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val nowMinutes: Int,
    val week: Week? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: TimetableError? = null,
    val isOffline: Boolean = false,
    val showPicker: Boolean = false,
    val rangeWarning: Boolean = false,
) {
    val hasSource: Boolean get() = !sourceKey.isNullOrBlank()
    val parityLabel: String? get() = week?.parityLabel
    val fetchedAt: Instant? get() = week?.fetchedAt
    val staleAt: String? get() = week?.staleAt
    val shareUrl: String? get() = SourceRef.parse(sourceKey)?.webUrl
    val isShowingToday: Boolean get() = selectedDate == today
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val prefs: UserPreferences,
    private val coordinator: RefreshCoordinator,
    private val deepLinkBus: DeepLinkBus,
    private val ticker: ClockTicker,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {

    private val today = TomskTime.today(clock)

    private val _state = MutableStateFlow(
        ScheduleUiState(
            monday = TomskTime.mondayOf(today),
            selectedDate = today,
            today = today,
            nowMinutes = TomskTime.nowMinutes(clock),
        ),
    )
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var cacheJob: Job? = null
    private var loadJob: Job? = null
    private var bootstrapped = false

    init {
        viewModelScope.launch { tick() }
        viewModelScope.launch { bootstrap() }
        viewModelScope.launch {
            // Ссылки из уведомлений и виджетов при запущенном приложении.
            deepLinkBus.pending.collect { link ->
                when (link) {
                    is AppLink.Day -> {
                        TomskTime.parseDate(link.date)?.let(::onSelectDay)
                        deepLinkBus.consume(link)
                    }

                    is AppLink.Source -> {
                        val date = TomskTime.parseDate(link.date) ?: _state.value.selectedDate
                        val ref = SourceRef.parse(link.key) ?: return@collect
                        prefs.setSelection(ref, null)
                        openSource(ref, date)
                        deepLinkBus.consume(link)
                    }

                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(ARG_SOURCE, null).drop(1).collect { raw ->
                val ref = SourceRef.parse(raw) ?: return@collect
                if (ref.key != _state.value.sourceKey) {
                    prefs.setSelection(ref, ref.displayLabel)
                    openSource(ref, _state.value.selectedDate)
                }
            }
        }
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(ARG_DATE, null).drop(1).collect { raw ->
                TomskTime.parseDate(raw)?.let(::onSelectDay)
            }
        }
    }

    fun onSelectDay(date: LocalDate) {
        val monday = TomskTime.mondayOf(date)
        if (monday != _state.value.monday) {
            loadWeek(monday, date)
        } else {
            persistDate(date)
            _state.update { it.copy(selectedDate = date) }
        }
    }

    fun onPreviousDay() = shiftDay(-1)

    fun onNextDay() = shiftDay(1)

    fun onPreviousWeek() = shiftWeek(-1)

    fun onNextWeek() = shiftWeek(1)

    fun onToday() {
        val date = TomskTime.today(clock)
        val monday = TomskTime.mondayOf(date)
        if (monday != _state.value.monday) {
            loadWeek(monday, date)
        } else {
            persistDate(date)
            _state.update { it.copy(selectedDate = date) }
        }
    }

    fun onRetry() {
        val ref = currentRef() ?: return
        viewModelScope.launch { refreshInternal(ref, _state.value.monday, force = true) }
    }

    fun onRefresh() {
        val ref = currentRef() ?: return
        viewModelScope.launch { refreshInternal(ref, _state.value.monday, force = true) }
    }

    fun onOpenPicker() = _state.update { it.copy(showPicker = true) }

    fun onClosePicker() = _state.update { it.copy(showPicker = false) }

    fun onRangeWarningShown() = _state.update { it.copy(rangeWarning = false) }

    /** Переход по ссылке с преподавателя/группы внутри расписания. */
    fun onExternalSource(key: String, label: String? = null) {
        val ref = SourceRef.parse(key)?.withLabel(label) ?: return
        viewModelScope.launch { prefs.setSelection(ref, label ?: ref.displayLabel) }
        openSource(ref, _state.value.selectedDate)
    }

    fun onSourceSelected(item: SearchItem) {
        val ref = item.source.withLabel(item.label)
        viewModelScope.launch { prefs.setSelection(ref, item.label) }
        openSource(ref, _state.value.selectedDate)
    }

    /** Повторный выбор уже сохранённого расписания (например, после ошибки сети). */
    fun onSavedSelected(selection: SourceSelection) {
        val ref = SourceRef.parse(selection.key)?.withLabel(selection.label) ?: return
        viewModelScope.launch { prefs.setSelection(ref, selection.label) }
        openSource(ref, _state.value.selectedDate)
    }

    fun onSourceSelectedFromCurrent() {
        val ref = currentRef() ?: return
        loadWeek(_state.value.monday, _state.value.selectedDate)
        viewModelScope.launch { prefs.setSelection(ref, ref.fallbackLabel) }
    }

    private suspend fun tick() {
        if (ticker.intervalMs <= 0L) return
        while (currentCoroutineContext().isActive) {
            TomskTime.now(clock).let { now ->
                val date = now.toLocalDate()
                _state.update {
                    it.copy(
                        today = date,
                        nowMinutes = now.hour * 60 + now.minute,
                    )
                }
            }
            ticker.awaitNextTick()
        }
    }

    private suspend fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true

        val stored = prefs.selectionOnce()
        val storedRef = SourceRef.parse(stored?.key)?.withLabel(stored?.label)
        val busLink = deepLinkBus.pending.value
        val argRef = SourceRef.parse(savedStateHandle.get<String>(ARG_SOURCE))
        val restoredDate = TomskTime.parseDate(savedStateHandle.get<String>(STATE_DATE))
        val savedTarget = SourceRef.parse(savedStateHandle.get<String>(STATE_SOURCE))
            ?.withLabel(savedStateHandle.get<String>(STATE_LABEL))
        val date = when (busLink) {
            is AppLink.Day -> TomskTime.parseDate(busLink.date) ?: TomskTime.today(clock)
            is AppLink.Source -> TomskTime.parseDate(busLink.date) ?: TomskTime.today(clock)
            else -> TomskTime.parseDate(savedStateHandle.get<String>(ARG_DATE))
                ?: restoredDate
                ?: TomskTime.today(clock)
        }

        // Ссылка из уведомления/виджета важнее сохранённого выбора.
        val linkRef = (busLink as? AppLink.Source)?.let { SourceRef.parse(it.key) }
        if (linkRef != null) {
            prefs.setSelection(linkRef, null)
            deepLinkBus.consume(busLink)
            openSource(linkRef, date)
            return
        }

        // Порядок: аргумент маршрута -> восстановленный после process death -> сохранённый выбор.
        val ref = argRef
            ?: savedTarget
            ?: storedRef?.withLabel(stored?.label)

        if (ref == null) {
            _state.update {
                it.copy(
                    monday = TomskTime.mondayOf(date),
                    selectedDate = date,
                    loading = false,
                    showPicker = true,
                )
            }
            return
        }

        if (argRef != null || savedTarget != null) prefs.setSelection(ref, ref.displayLabel)
        openSource(ref, date)
    }

    private fun shiftDay(delta: Long) {
        val date = _state.value.selectedDate.plusDays(delta)
        val monday = TomskTime.mondayOf(date)
        if (monday == _state.value.monday) {
            persistDate(date)
            _state.update { it.copy(selectedDate = date) }
            return
        }
        if (!TomskTime.isWithinWindow(monday, TomskTime.today(clock))) {
            _state.update { it.copy(rangeWarning = true) }
            return
        }
        loadWeek(monday, date)
    }

    private fun shiftWeek(delta: Long) {
        val targetMonday = _state.value.monday.plusWeeks(delta)
        if (!TomskTime.isWithinWindow(targetMonday, TomskTime.today(clock))) {
            _state.update { it.copy(rangeWarning = true) }
            return
        }
        loadWeek(targetMonday, _state.value.selectedDate.plusWeeks(delta))
    }

    private fun openSource(ref: SourceRef, date: LocalDate) {
        val monday = TomskTime.mondayOf(date)
        persistDate(date)
        _state.update {
            it.copy(
                sourceKey = ref.key,
                sourceLabel = ref.fallbackLabel,
                monday = monday,
                selectedDate = date,
                week = null,
                loading = true,
                refreshing = false,
                error = null,
                isOffline = false,
                showPicker = false,
                rangeWarning = false,
            )
        }
        observeAndLoad(ref, monday)
    }

    private fun loadWeek(monday: LocalDate, date: LocalDate) {
        persistDate(date)
        _state.update {
            it.copy(
                monday = monday,
                selectedDate = date,
                week = null,
                loading = true,
                error = null,
                isOffline = false,
            )
        }
        val ref = currentRef() ?: return
        observeAndLoad(ref, monday)
    }

    private fun observeAndLoad(ref: SourceRef, monday: LocalDate) {
        cacheJob?.cancel()
        loadJob?.cancel()

        cacheJob = viewModelScope.launch {
            repository.observeCached(ref, monday).collect { cached ->
                if (cached != null) {
                    _state.update {
                        it.copy(
                            week = cached,
                            loading = false,
                            isOffline = it.error != null,
                        )
                    }
                }
            }
        }

        loadJob = viewModelScope.launch { refreshInternal(ref, monday, force = false) }
    }

    private suspend fun refreshInternal(ref: SourceRef, monday: LocalDate, force: Boolean) {
        _state.update { it.copy(loading = it.week == null, refreshing = true) }
        val result = repository.refresh(ref, monday, force)
        result.fold(
            onSuccess = { week ->
                _state.update {
                    it.copy(
                        week = week,
                        loading = false,
                        refreshing = false,
                        error = null,
                        isOffline = false,
                    )
                }
                coordinator.onScheduleUpdated()
            },
            onFailure = { throwable ->
                val error = (throwable as? TimetableException)?.error
                    ?: TimetableError(TimetableErrorKind.UNKNOWN)
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = error,
                        isOffline = it.week != null,
                    )
                }
            },
        )
    }

    private fun currentRef(): SourceRef? = SourceRef.parse(_state.value.sourceKey)
        ?.withLabel(_state.value.sourceLabel)

    private fun persistDate(date: LocalDate) {
        savedStateHandle[STATE_DATE] = date.toString()
        _state.value.sourceKey?.let { key ->
            savedStateHandle[STATE_SOURCE] = key
            savedStateHandle[STATE_LABEL] = _state.value.sourceLabel
        }
    }

    private companion object {
        const val ARG_SOURCE = "source"
        const val ARG_DATE = "date"
        const val STATE_DATE = "vm_date"
        const val STATE_SOURCE = "vm_source"
        const val STATE_LABEL = "vm_label"
    }
}
