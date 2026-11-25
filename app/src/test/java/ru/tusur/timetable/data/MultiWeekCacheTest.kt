package ru.tusur.timetable.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.tusur.timetable.data.db.EventEntity
import ru.tusur.timetable.data.db.PersonEntity
import ru.tusur.timetable.data.db.SearchCacheEntity
import ru.tusur.timetable.data.db.TimetableDao
import ru.tusur.timetable.data.db.WeekEntity
import ru.tusur.timetable.data.db.toDomain
import ru.tusur.timetable.data.db.toEventEntities
import ru.tusur.timetable.data.db.toPersonEntities
import ru.tusur.timetable.data.db.toWeekEntity
import ru.tusur.timetable.data.mapper.ProxyResponseMapper
import ru.tusur.timetable.data.network.ApiErrorMapper
import ru.tusur.timetable.data.network.OfficialRemoteSource
import ru.tusur.timetable.data.network.OfficialTusurApi
import ru.tusur.timetable.data.network.ProxyRemoteSource
import ru.tusur.timetable.data.network.ProxyTimetableApi
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.data.repository.OfflineFirstTimetableRepository
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.SourceRef
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Кэш двух недель и двух групп: переключение не должно затирать чужую неделю.
 * Найдено аудитом как непокрытый сценарий.
 */
class MultiWeekCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: MapDao
    private lateinit var repository: OfflineFirstTimetableRepository

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 23)
    private val nextMonday = LocalDate.of(2026, 11, 30)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ProxyTimetableApi::class.java)

        dao = MapDao()
        val prefs = mockk<UserPreferences>(relaxed = true)
        every { prefs.settings } returns kotlinx.coroutines.flow.flowOf(AppSettings.Default)

        repository = OfflineFirstTimetableRepository(
            dao = dao,
            proxy = ProxyRemoteSource(api, ProxyResponseMapper()),
            official = OfficialRemoteSource(mockk<OfficialTusurApi>(relaxed = true)),
            prefs = prefs,
            errorMapper = ApiErrorMapper(json),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun weekJson(mondayIso: String, title: String, lessonTitle: String, lessonDate: String): String = """
        {
          "title": "$title", "teacher": false, "monday": "$mondayIso",
          "today": "2026-11-25", "parity": "нечётная",
          "days": [{"date": "$lessonDate", "events": [
            {"start":"16:45","end":"18:20","pair":5,"title":"$lessonTitle",
             "kind":"Лекция","place":"ф 331б","who":null,"people":[],
             "note":null,"floor":"3 этаж","map":"/map?r=331%D0%B1"}
          ]}],
          "source": null, "stale": null
        }
    """.trimIndent()

    @Test
    fun `two consecutive weeks of one group coexist`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson("2026-11-23", "гр. 023", "ПЛИС", "2026-11-23")))
        server.enqueue(MockResponse().setBody(weekJson("2026-11-30", "гр. 023", "Физика", "2026-11-30")))

        repository.refresh(SourceRef.group("fit/023"), monday)
        repository.refresh(SourceRef.group("fit/023"), nextMonday)

        val first = repository.cachedWeek(SourceRef.group("fit/023"), monday)
        val second = repository.cachedWeek(SourceRef.group("fit/023"), nextMonday)

        assertEquals("ПЛИС", first?.days?.first { it.date == monday }?.lessons?.single()?.title)
        assertEquals("Физика", second?.days?.first { it.date == nextMonday }?.lessons?.single()?.title)
        // Событие второй недели не просочилось в первую.
        assertTrue(first!!.days.none { it.date == nextMonday })
    }

    @Test
    fun `switching group keeps previous group cache`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson("2026-11-23", "гр. 023", "ПЛИС", "2026-11-23")))
        server.enqueue(MockResponse().setBody(weekJson("2026-11-23", "гр. 016", "Химия", "2026-11-23")))

        repository.refresh(SourceRef.group("fit/023"), monday)
        repository.refresh(SourceRef.group("fit/016"), monday)

        val first = repository.cachedWeek(SourceRef.group("fit/023"), monday)
        val second = repository.cachedWeek(SourceRef.group("fit/016"), monday)

        assertEquals("гр. 023", first?.title)
        assertEquals("гр. 016", second?.title)
        assertEquals("ПЛИС", first?.days?.first { it.date == monday }?.lessons?.single()?.title)
        assertEquals("Химия", second?.days?.first { it.date == monday }?.lessons?.single()?.title)
        assertNotEquals(first?.sourceKey, second?.sourceKey)
    }

    @Test
    fun `week saved without day list still restores full week`() {
        val source = SourceRef.group("fit/023")
        val week = ru.tusur.timetable.domain.model.Week(
            sourceKey = source.key,
            title = "гр. 023",
            isTeacher = false,
            monday = monday,
            today = null,
            parity = "нечётная",
            days = (0..6).map {
                ru.tusur.timetable.domain.model.ScheduleDay(monday.plusDays(it.toLong()), emptyList())
            },
            sourceUrl = null,
            staleAt = null,
            fetchedAt = Instant.EPOCH,
        )
        // Имитируем старую запись: список дней пуст, события есть только за понедельник.
        val entity = week.toWeekEntity().copy(days = "")
        val events = week.toEventEntities()
        val restored = entity.toDomain(events, week.toPersonEntities())

        assertEquals(7, restored.days.size)
        assertEquals(monday, restored.days.first().date)
    }

    @Test
    fun `cached week of another group is not returned`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson("2026-11-23", "гр. 023", "ПЛИС", "2026-11-23")))
        repository.refresh(SourceRef.group("fit/023"), monday)

        assertNull(repository.cachedWeek(SourceRef.group("fit/999"), monday))
    }

    /** DAO на Map: умеет хранить несколько недель и групп одновременно. */
    private class MapDao : TimetableDao() {
        private val weeks = mutableMapOf<String, WeekEntity>()
        private val events = mutableMapOf<String, MutableList<EventEntity>>()
        private val people = mutableMapOf<String, MutableList<PersonEntity>>()
        private val search = mutableMapOf<String, SearchCacheEntity>()

        private fun key(sourceKey: String, monday: String) = "$sourceKey@$monday"

        override fun observeWeek(sourceKey: String, monday: String) =
            MutableStateFlow(weeks[key(sourceKey, monday)])

        override fun observeEvents(sourceKey: String, monday: String) =
            MutableStateFlow(events[key(sourceKey, monday)].orEmpty().toList())

        override fun observePeople(sourceKey: String, monday: String) =
            MutableStateFlow(people[key(sourceKey, monday)].orEmpty().toList())

        override suspend fun weekOnce(sourceKey: String, monday: String) = weeks[key(sourceKey, monday)]

        override suspend fun eventsOnce(sourceKey: String, monday: String) =
            events[key(sourceKey, monday)].orEmpty().toList()

        override suspend fun peopleOnce(sourceKey: String, monday: String) =
            people[key(sourceKey, monday)].orEmpty().toList()

        override suspend fun cachedMondays(sourceKey: String, limit: Int) = emptyList<String>()

        override suspend fun upsertWeek(week: WeekEntity) {
            weeks[key(week.sourceKey, week.monday)] = week
        }

        override suspend fun upsertEvents(events: List<EventEntity>) {
            events.forEach { e ->
                this.events.getOrPut(key(e.sourceKey, e.monday)) { mutableListOf() }.add(e)
            }
        }

        override suspend fun upsertPeople(people: List<PersonEntity>) {
            people.forEach { p ->
                this.people.getOrPut(key(p.sourceKey, p.monday)) { mutableListOf() }.add(p)
            }
        }

        override suspend fun deleteEvents(sourceKey: String, monday: String) {
            events.remove(key(sourceKey, monday))
        }

        override suspend fun deletePeople(sourceKey: String, monday: String) {
            people.remove(key(sourceKey, monday))
        }

        override suspend fun deleteWeek(sourceKey: String, monday: String) {
            weeks.remove(key(sourceKey, monday))
        }

        override suspend fun upsertSearch(items: List<SearchCacheEntity>) {
            items.forEach { search["${it.type}:${it.id}"] = it }
        }

        override suspend fun recentSearches(limit: Int) =
            search.values.sortedByDescending { it.lastSeenAt }.take(limit)

        override suspend fun offlineSearch(like: String): List<SearchCacheEntity> {
            val needle = like.removeSurrounding("%")
            return search.values.filter { it.id.contains(needle) || it.label.contains(needle) }
        }

        override suspend fun teacherLabel(teacherId: String) =
            search.values.firstOrNull { it.type == "teacher" && it.teacherId == teacherId }?.label

        override suspend fun pruneSearchCache(threshold: Long) = Unit
    }
}
