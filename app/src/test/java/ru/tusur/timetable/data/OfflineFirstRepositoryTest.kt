package ru.tusur.timetable.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.tusur.timetable.data.db.EventEntity
import ru.tusur.timetable.data.db.PersonEntity
import ru.tusur.timetable.data.db.SearchCacheEntity
import ru.tusur.timetable.data.db.WeekEntity
import ru.tusur.timetable.data.db.TimetableDao
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
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.domain.model.TimetableException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Репозиторий без Android: MockWebServer как сеть, in-memory DAO как Room.
 * Проверяет главный сценарий приложения — «показать кэш, обновить в фоне».
 */
class OfflineFirstRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeDao
    private lateinit var prefs: UserPreferences
    private lateinit var repository: OfflineFirstTimetableRepository

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 23)

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

        val officialApi = mockk<OfficialTusurApi>(relaxed = true)
        val mapper = ProxyResponseMapper()

        dao = FakeDao()
        prefs = mockk(relaxed = true)
        every { prefs.settings } returns flowOf(AppSettings.Default)

        repository = OfflineFirstTimetableRepository(
            dao = dao,
            proxy = ProxyRemoteSource(api, mapper),
            official = OfficialRemoteSource(officialApi),
            prefs = prefs,
            errorMapper = ApiErrorMapper(json),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun weekJson(
        title: String = "гр. 023",
        parity: String = "нечётная",
        stale: String? = null,
    ): String = """
        {
          "title": "$title", "teacher": false, "monday": "2026-11-23",
          "today": "2026-11-25", "parity": "$parity",
          "days": [
            {"date": "2026-11-23", "events": [
              {"start": "16:45", "end": "18:20", "pair": 5, "title": "Программирование ПЛИС",
               "kind": "Лабораторная работа", "place": "ф 331б", "who": "Костина Мария Алексеевна",
               "people": [{"label": "Костина Мария Алексеевна", "teacher": "35612"}],
               "note": "", "floor": "3 этаж", "map": "/map?r=331%D0%B1"}
            ]},
            {"date": "2026-11-24", "events": []},
            {"date": "2026-11-25", "events": []},
            {"date": "2026-11-26", "events": []},
            {"date": "2026-11-27", "events": []},
            {"date": "2026-11-28", "events": []},
            {"date": "2026-11-29", "events": []}
          ],
          "source": "https://timetable.tusur.ru/faculties/fit/groups/023",
          "stale": ${stale?.let { "\"$it\"" } ?: "null"}
        }
    """.trimIndent()

    @Test
    fun `refresh persists week and returns it`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson()))

        val result = repository.refresh(SourceRef.group("fit/023"), monday)

        assertTrue(result.isSuccess)
        val week = result.getOrThrow()
        assertEquals("гр. 023", week.title)
        assertEquals(7, week.days.size)
        assertNotNull(dao.week)

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("monday=2026-11-23"))
        assertTrue(request.path!!.contains("group=fit%2F023"))
    }

    @Test
    fun `cached week is readable after refresh without network`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson()))
        repository.refresh(SourceRef.group("fit/023"), monday)

        val cached = repository.cachedWeek(SourceRef.group("fit/023"), monday)

        assertNotNull(cached)
        assertEquals(1, cached!!.totalLessons)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `observed cache emits stored week`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson()))
        repository.refresh(SourceRef.group("fit/023"), monday)

        val flow: Flow<ru.tusur.timetable.domain.model.Week?> =
            repository.observeCached(SourceRef.group("fit/023"), monday)

        var emitted: ru.tusur.timetable.domain.model.Week? = null
        flow.first().let { emitted = it }

        assertNotNull(emitted)
        assertEquals(1, emitted!!.days.first().lessons.size)
    }

    @Test
    fun `memory ttl prevents duplicate request within five minutes`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson()))
        repository.refresh(SourceRef.group("fit/023"), monday)
        repository.refresh(SourceRef.group("fit/023"), monday)

        assertEquals(1, server.requestCount)

        // force = true обязан пойти в сеть даже при свежем кэше.
        server.enqueue(MockResponse().setBody(weekJson()))
        repository.refresh(SourceRef.group("fit/023"), monday, force = true)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `unknown target is reported as source gone`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"error":"unknown_target"}"""),
        )

        val failure = repository.refresh(SourceRef.group("fit/023"), monday).exceptionOrNull()

        assertTrue(failure is TimetableException)
        assertEquals(
            TimetableErrorKind.UNKNOWN_TARGET,
            (failure as TimetableException).error.kind,
        )
        assertTrue(failure.error.isSourceGone)
        assertNull(dao.week)
    }

    @Test
    fun `week outside thirty week window is rejected without network`() = runTest {
        val farMonday = monday.plusWeeks(40)

        val failure = repository.refresh(SourceRef.group("fit/023"), farMonday).exceptionOrNull()

        assertTrue(failure is TimetableException)
        assertEquals(TimetableErrorKind.BAD_WEEK, (failure as TimetableException).error.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `stale flag survives refresh`() = runTest {
        server.enqueue(MockResponse().setBody(weekJson(stale = "18:30")))

        val week = repository.refresh(SourceRef.group("fit/023"), monday).getOrThrow()

        assertEquals("18:30", week.staleAt)
        assertTrue(week.stale)
    }

    @Test
    fun `search caches results and falls back to them offline`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"found":[{"type":"group","faculty":"fit","group":"023","label":"гр. 023","hint":"fit","only":true}]}""",
            ),
        )
        val online = repository.search("023").getOrThrow()
        assertEquals(1, online.size)

        server.shutdown()
        val offline = repository.search("023").getOrThrow()
        assertEquals(1, offline.size)
        assertEquals("гр. 023", offline.single().label)
    }

    @Test
    fun `query shorter than two characters never hits network`() = runTest {
        val result = repository.search("0").getOrThrow()
        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }

    /** Минимальная in-memory реализация DAO: тест не тянет Room и Android. */
    private class FakeDao : TimetableDao() {
        var week: WeekEntity? = null
        val events = mutableListOf<EventEntity>()
        val people = mutableListOf<PersonEntity>()
        val search = mutableMapOf<String, SearchCacheEntity>()

        override fun observeWeek(sourceKey: String, monday: String): Flow<WeekEntity?> =
            MutableStateFlow(week?.takeIf { it.sourceKey == sourceKey && it.monday == monday })

        override fun observeEvents(sourceKey: String, monday: String): Flow<List<EventEntity>> =
            MutableStateFlow(eventsFor(sourceKey, monday))

        override fun observePeople(sourceKey: String, monday: String): Flow<List<PersonEntity>> =
            MutableStateFlow(peopleFor(sourceKey, monday))

        override suspend fun weekOnce(sourceKey: String, monday: String): WeekEntity? =
            week?.takeIf { it.sourceKey == sourceKey && it.monday == monday }

        override suspend fun eventsOnce(sourceKey: String, monday: String): List<EventEntity> =
            eventsFor(sourceKey, monday)

        override suspend fun peopleOnce(sourceKey: String, monday: String): List<PersonEntity> =
            peopleFor(sourceKey, monday)

        override suspend fun cachedMondays(sourceKey: String, limit: Int): List<String> =
            listOfNotNull(week?.takeIf { it.sourceKey == sourceKey }?.monday).take(limit)

        override suspend fun upsertWeek(week: WeekEntity) {
            this.week = week
        }

        override suspend fun upsertEvents(events: List<EventEntity>) {
            this.events += events
        }

        override suspend fun upsertPeople(people: List<PersonEntity>) {
            this.people += people
        }

        override suspend fun deleteEvents(sourceKey: String, monday: String) {
            events.removeAll { it.sourceKey == sourceKey && it.monday == monday }
        }

        override suspend fun deletePeople(sourceKey: String, monday: String) {
            people.removeAll { it.sourceKey == sourceKey && it.monday == monday }
        }

        override suspend fun deleteWeek(sourceKey: String, monday: String) {
            if (week?.sourceKey == sourceKey && week?.monday == monday) week = null
        }

        override suspend fun upsertSearch(items: List<SearchCacheEntity>) {
            items.forEach { search["${it.type}:${it.id}"] = it }
        }

        override suspend fun recentSearches(limit: Int): List<SearchCacheEntity> =
            search.values.sortedByDescending { it.lastSeenAt }.take(limit)

        override suspend fun offlineSearch(like: String): List<SearchCacheEntity> {
            val needle = like.removeSurrounding("%")
            return search.values.filter { it.id.contains(needle) || it.label.contains(needle) }
        }

        override suspend fun teacherLabel(teacherId: String): String? =
            search.values.firstOrNull { it.type == "teacher" && it.teacherId == teacherId }?.label

        override suspend fun pruneSearchCache(threshold: Long) {
            search.entries.removeAll { it.value.lastSeenAt < threshold }
        }

        private fun eventsFor(sourceKey: String, monday: String) =
            events.filter { it.sourceKey == sourceKey && it.monday == monday }.sortedBy { it.position }

        private fun peopleFor(sourceKey: String, monday: String) =
            people.filter { it.sourceKey == sourceKey && it.monday == monday }
    }
}
