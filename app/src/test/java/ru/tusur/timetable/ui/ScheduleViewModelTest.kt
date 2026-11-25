package ru.tusur.timetable.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.data.repository.RefreshCoordinator
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.repository.TimetableRepository
import ru.tusur.timetable.ui.navigation.AppLink
import ru.tusur.timetable.ui.navigation.DeepLinkBus
import ru.tusur.timetable.ui.schedule.ScheduleViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * ViewModel запускает бесконечный тикер минут, поэтому вместо advanceUntilIdle()
 * прокручиваем текущие задачи (runCurrent) и гасим scope в конце теста.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 25)
    private val monday = LocalDate.of(2026, 11, 23)

    private var viewModel: ScheduleViewModel? = null

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
    }

    private fun week(ref: SourceRef, date: LocalDate) = Week(
        sourceKey = ref.key,
        title = ref.fallbackLabel,
        isTeacher = ref.isTeacher,
        monday = date,
        today = today,
        parity = "нечётная",
        days = (0..6).map { offset ->
            ScheduleDay(date.plusDays(offset.toLong()), emptyList())
        },
        sourceUrl = null,
        staleAt = null,
        fetchedAt = Instant.parse("2026-11-25T10:00:00Z"),
    )

    private inner class FakeRepository : TimetableRepository {
        val cache = mutableMapOf<String, MutableStateFlow<Week?>>()
        var refreshes = 0

        override fun observeCached(ref: SourceRef, monday: LocalDate) =
            cache.getOrPut("${ref.key}@$monday") { MutableStateFlow(null) }

        override suspend fun cachedWeek(ref: SourceRef, monday: LocalDate): Week? = null

        override suspend fun refresh(ref: SourceRef, monday: LocalDate, force: Boolean): Result<Week> {
            refreshes++
            val value = week(ref, monday)
            cache.getOrPut("${ref.key}@$monday") { MutableStateFlow(null) }.value = value
            return Result.success(value)
        }

        override suspend fun search(query: String) = Result.success(emptyList<SearchItem>())

        override suspend fun recentSearches(limit: Int) = emptyList<SearchItem>()

        override suspend fun teacherLabel(teacherId: String): String? = null
    }

    private fun build(
        repository: TimetableRepository,
        prefs: UserPreferences,
        bus: DeepLinkBus = DeepLinkBus(),
    ): ScheduleViewModel {
        val model = ScheduleViewModel(
            repository = repository,
            prefs = prefs,
            coordinator = mockk<RefreshCoordinator>(relaxed = true),
            deepLinkBus = bus,
            ticker = ru.tusur.timetable.ui.schedule.DefaultClockTicker(intervalMs = 0L),
            savedStateHandle = SavedStateHandle(),
            clock = clock,
        )
        viewModel = model
        return model
    }

    private fun mockPrefs(selection: SourceSelection? = null): UserPreferences {
        val prefs = mockk<UserPreferences>(relaxed = true)
        every { prefs.settings } returns flowOf(AppSettings.Default)
        coEvery { prefs.selectionOnce() } returns selection
        return prefs
    }

    private fun TestScope.settle() {
        repeat(5) {
            runCurrent()
        }
    }

    @Test
    fun `deep link opens schedule even without saved selection`() = runTest {
        val bus = DeepLinkBus()
        bus.post(AppLink.Source("group:fit/023"))

        val model = build(FakeRepository(), mockPrefs(), bus)
        settle()

        val state = model.state.value
        assertEquals("group:fit/023", state.sourceKey)
        assertEquals(false, state.showPicker)
        assertEquals(monday, state.monday)
        assertEquals(null, bus.pending.value)
    }

    @Test
    fun `deep link switches already open schedule`() = runTest {
        val repository = FakeRepository()
        val bus = DeepLinkBus()
        val model = build(repository, mockPrefs(SourceSelection("group:fit/023", "гр. 023")), bus)
        settle()
        assertEquals("group:fit/023", model.state.value.sourceKey)

        bus.post(AppLink.Source("teacher:35612"))
        settle()

        assertEquals("teacher:35612", model.state.value.sourceKey)
        assertEquals(null, bus.pending.value)
        assertTrue(repository.refreshes >= 2)
    }

    @Test
    fun `restored selection is opened on cold start`() = runTest {
        val model = build(
            FakeRepository(),
            mockPrefs(SourceSelection("group:fit/436-1", "гр. 436-1")),
        )
        settle()

        val state = model.state.value
        assertEquals("group:fit/436-1", state.sourceKey)
        assertEquals("гр. 436-1", state.sourceLabel)
        assertEquals(false, state.loading)
        assertEquals(7, state.week?.days?.size)
    }

    @Test
    fun `no selection opens picker`() = runTest {
        val model = build(FakeRepository(), mockPrefs())
        settle()

        assertTrue(model.state.value.showPicker)
        assertEquals(null, model.state.value.sourceKey)
    }

    @Test
    fun `today resets day to tomsk date`() = runTest {
        val model = build(FakeRepository(), mockPrefs(SourceSelection("group:fit/023", "гр. 023")))
        settle()

        model.onSelectDay(LocalDate.of(2026, 11, 23))
        settle()
        model.onToday()
        settle()

        // 10:00 UTC = 17:00 в Томске (UTC+7), значит «сегодня» = 25.11.2026.
        assertEquals(today, model.state.value.selectedDate)
        assertEquals(today, model.state.value.today)
    }

    @Test
    fun `day deep link selects requested date`() = runTest {
        val bus = DeepLinkBus()
        val model = build(FakeRepository(), mockPrefs(SourceSelection("group:fit/023", "гр. 023")), bus)
        settle()

        bus.post(AppLink.Day("2026-11-24"))
        settle()

        assertEquals(LocalDate.of(2026, 11, 24), model.state.value.selectedDate)
        assertEquals(null, bus.pending.value)
    }
}
