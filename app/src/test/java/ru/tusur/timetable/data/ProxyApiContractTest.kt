package ru.tusur.timetable.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.tusur.timetable.data.mapper.ProxyResponseMapper
import ru.tusur.timetable.data.network.ApiErrorMapper
import ru.tusur.timetable.data.network.ProxyTimetableApi
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.TimetableErrorKind
import java.time.Instant
import java.util.concurrent.TimeUnit

class ProxyApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProxyTimetableApi
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }
    private val mapper = ProxyResponseMapper()
    private val errorMapper = ApiErrorMapper(json)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ProxyTimetableApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun fetchWeek(): ru.tusur.timetable.domain.model.Week {
        val dto = api.week(monday = "2026-11-23", group = "fit/023", teacher = null)
        return mapper.mapWeek(dto, SourceRef.group("fit/023"), Instant.parse("2026-11-25T10:00:00Z"))
    }

    @Test
    fun `week response maps days, people and map room`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "title": "гр. 023",
                  "teacher": false,
                  "monday": "2026-11-23",
                  "today": "2026-11-25",
                  "parity": "нечётная",
                  "days": [
                    {"date": "2026-11-23", "events": [
                      {"start": "16:45", "end": "18:20", "pair": 5,
                       "title": "Программирование ПЛИС", "kind": "Лабораторная работа",
                       "place": "ф 331б", "who": "Костина Мария Алексеевна",
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
                  "stale": null
                }
                """.trimIndent(),
            ),
        )

        val week = fetchWeek()

        assertEquals("гр. 023", week.title)
        assertEquals(7, week.days.size)
        assertEquals("нечётная неделя", week.parityLabel)
        assertEquals("2026-11-25", week.today.toString())

        val lesson = week.days.first().lessons.single()
        assertEquals(5, lesson.pair)
        assertEquals(16 * 60 + 45, lesson.startMinutes)
        assertEquals(LessonType.LAB, lesson.type)
        assertEquals("331б", lesson.mapRoom)
        assertEquals("teacher:35612", lesson.people.single().sourceKey)
        assertTrue(lesson.hasMap)
    }

    @Test
    fun `stale week keeps timestamp`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {"title":"гр. 023","teacher":false,"monday":"2026-11-23",
                 "today":"2026-11-25","parity":null,"days":[],
                 "source":null,"stale":"18:30"}
                """.trimIndent(),
            ),
        )
        val week = fetchWeek()
        assertEquals("18:30", week.staleAt)
        assertTrue(week.stale)
        assertNull(week.parityLabel)
    }

    @Test
    fun `404 unknown target maps to source gone`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"error":"unknown_target"}"""),
        )
        val failure = runCatching { fetchWeek() }.exceptionOrNull()
        val error = errorMapper.map(failure ?: error("expected failure"))
        assertEquals(TimetableErrorKind.UNKNOWN_TARGET, error.kind)
        assertTrue(error.isSourceGone)
    }

    @Test
    fun `429 maps to rate limited`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited"}"""),
        )
        val failure = runCatching { fetchWeek() }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(TimetableErrorKind.RATE_LIMITED, errorMapper.map(failure!!).kind)
    }

    @Test
    fun `500 maps to unavailable`() = kotlinx.coroutines.runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("oops"))
        val failure = runCatching { fetchWeek() }.exceptionOrNull()
        assertTrue(failure is HttpException)
        assertEquals(TimetableErrorKind.UNAVAILABLE, errorMapper.map(failure!!).kind)
    }

    @Test
    fun `timeout is reported as timeout`() = kotlinx.coroutines.runBlocking {
        val timeoutApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .readTimeout(200, TimeUnit.MILLISECONDS)
                    .build(),
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ProxyTimetableApi::class.java)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val failure = runCatching {
            timeoutApi.week(monday = "2026-11-23", group = "fit/023", teacher = null)
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(TimetableErrorKind.TIMEOUT, errorMapper.map(failure!!).kind)
    }
}
