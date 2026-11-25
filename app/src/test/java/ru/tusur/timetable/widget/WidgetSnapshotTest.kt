package ru.tusur.timetable.widget

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.db.WidgetDao
import ru.tusur.timetable.data.db.WidgetPrefsEntity
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.repository.TimetableRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Данные для виджетов: проверяем, что снапшот отражает «идёт / следующая / пусто»
 * и что настраиваемый виджет читает своё расписание, а не общее.
 */
class WidgetSnapshotTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)
    private val today: LocalDate = TomskTime.today(clock)
    private val monday: LocalDate = TomskTime.mondayOf(today)

    private lateinit var repository: FakeRepository
    private lateinit var widgetDao: FakeWidgetDao
    private lateinit var prefs: UserPreferences
    private lateinit var provider: WidgetSnapshotProvider

    @Before
    fun setUp() {
        repository = FakeRepository(today, monday)
        widgetDao = FakeWidgetDao()
        prefs = mockk(relaxed = true)
        every { prefs.settings } returns flowOf(AppSettings.Default)
        coEvery { prefs.selectionOnce() } returns SourceSelection("group:fit/023", "гр. 023")

        provider = WidgetSnapshotProvider(
            repository = repository,
            prefs = prefs,
            widgetDao = widgetDao,
            clock = clock,
        )
    }

    private fun lesson(start: Int, end: Int, pair: Int, title: String, place: String) = Lesson(
        position = pair,
        date = today,
        startMinutes = start,
        endMinutes = end,
        pair = pair,
        title = title,
        kindRaw = "Лекция",
        type = LessonType.LECTURE,
        place = place,
        who = null,
    )

    @Test
    fun `no source means empty widget state`() = runTest {
        coEvery { prefs.selectionOnce() } returns null
        val snapshot = provider.forApp()

        assertFalse(snapshot.hasSource)
        assertTrue(snapshot.isEmpty)
        assertEquals(today, snapshot.today)
    }

    @Test
    fun `live lesson is reported with minutes left`() = runTest {
        // 10:00 UTC = 17:00 в Томске (UTC+7): пара 16:45–18:20 идёт.
        repository.week = weekOf(
            listOf(lesson(16 * 60 + 45, 18 * 60 + 20, 5, "Программирование ПЛИС", "ф 331б")),
        )

        val snapshot = provider.forApp()

        assertTrue(snapshot.hasSource && snapshot.hasCache)
        assertEquals(18 * 60 + 20 - 17 * 60, snapshot.status.minutesLeft)
        assertEquals("Программирование ПЛИС", snapshot.status.live?.title)
        assertNull(snapshot.status.next)
    }

    @Test
    fun `next lesson is reported between pairs`() = runTest {
        repository.week = weekOf(
            listOf(
                lesson(8 * 60 + 50, 10 * 60 + 25, 1, "Физика", "ф 220"),
                lesson(18 * 60 + 30, 20 * 60 + 5, 6, "Схемотехника", "ф 414"),
            ),
        )

        val snapshot = provider.forApp()

        assertNull(snapshot.status.live)
        assertEquals("Схемотехника", snapshot.status.next?.title)
        assertEquals(18 * 60 + 30 - 17 * 60, snapshot.status.minutesToNext)
        assertEquals(2, snapshot.lessons.size)
    }

    @Test
    fun `finished day is reported as all finished`() = runTest {
        repository.week = weekOf(listOf(lesson(8 * 60 + 50, 10 * 60 + 25, 1, "Физика", "ф 220")))

        val snapshot = provider.forApp()

        assertTrue(snapshot.status.allFinished)
        assertFalse(snapshot.status.isEmpty)
        assertNull(snapshot.status.live)
        assertNull(snapshot.status.next)
    }

    @Test
    fun `empty day has no lessons but is not missing data`() = runTest {
        repository.week = weekOf(emptyList())

        val snapshot = provider.forApp()

        assertTrue(snapshot.hasCache)
        assertTrue(snapshot.lessons.isEmpty())
        assertTrue(snapshot.status.isEmpty)
    }

    @Test
    fun `week widget sees all seven days`() = runTest {
        repository.week = weekOf(emptyList())

        val snapshot = provider.forApp()

        assertEquals(7, snapshot.week.size)
        assertEquals(monday, snapshot.week.first().date)
        assertEquals(monday.plusDays(6), snapshot.week.last().date)
    }

    @Test
    fun `quick widget uses its own selection, not the app one`() = runTest {
        widgetDao.entities["widget-1"] = WidgetPrefsEntity(
            widgetId = "widget-1",
            sourceKey = "teacher:35612",
            label = "Костина М.А.",
            mode = WidgetDisplayMode.TODAY.name,
            updatedAt = 0L,
        )
        repository.week = weekOf(listOf(lesson(8 * 60 + 50, 10 * 60 + 25, 1, "Физика", "ф 220")))

        val (snapshot, mode) = provider.forWidget("widget-1")

        assertEquals(WidgetDisplayMode.TODAY, mode)
        assertEquals("teacher:35612", snapshot.sourceKey)
        assertEquals("teacher:35612", repository.lastRequested?.key)
    }

    @Test
    fun `quick widget without config shows setup state`() = runTest {
        val (snapshot, mode) = provider.forWidget("unknown-widget")

        assertFalse(snapshot.hasSource)
        assertTrue(snapshot.isEmpty)
        assertEquals(WidgetDisplayMode.NEXT, mode)
    }

    @Test
    fun `display mode parses unknown values safely`() {
        assertEquals(WidgetDisplayMode.NEXT, WidgetDisplayMode.from(null))
        assertEquals(WidgetDisplayMode.NEXT, WidgetDisplayMode.from("garbage"))
        assertEquals(WidgetDisplayMode.TODAY, WidgetDisplayMode.from("TODAY"))
    }

    private fun weekOf(lessons: List<Lesson>) = Week(
        sourceKey = "group:fit/023",
        title = "гр. 023",
        isTeacher = false,
        monday = monday,
        today = today,
        parity = "нечётная",
        days = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            ScheduleDay(date, if (date == today) lessons else emptyList())
        },
        sourceUrl = null,
        staleAt = null,
        fetchedAt = Instant.now(clock),
    )



    private class FakeRepository(
        private val today: LocalDate,
        private val monday: LocalDate,
    ) : TimetableRepository {
        var week: Week? = null
        var lastRequested: SourceRef? = null

        override fun observeCached(ref: SourceRef, monday: LocalDate) = flowOf(week)

        override suspend fun cachedWeek(ref: SourceRef, monday: LocalDate): Week? {
            lastRequested = ref
            return week?.takeIf { it.monday == monday }
        }

        override suspend fun refresh(ref: SourceRef, monday: LocalDate, force: Boolean) =
            Result.success(week ?: error("no week"))

        override suspend fun search(query: String) = Result.success(emptyList<ru.tusur.timetable.domain.model.SearchItem>())

        override suspend fun recentSearches(limit: Int) = emptyList<ru.tusur.timetable.domain.model.SearchItem>()

        override suspend fun teacherLabel(teacherId: String): String? = null
    }

    private class FakeWidgetDao : WidgetDao {
        val entities = mutableMapOf<String, WidgetPrefsEntity>()

        override suspend fun get(widgetId: String): WidgetPrefsEntity? = entities[widgetId]

        override suspend fun upsert(entity: WidgetPrefsEntity) {
            entities[entity.widgetId] = entity
        }

        override suspend fun delete(widgetId: String) {
            entities.remove(widgetId)
        }

        override suspend fun all(): List<WidgetPrefsEntity> = entities.values.toList()
    }
}
