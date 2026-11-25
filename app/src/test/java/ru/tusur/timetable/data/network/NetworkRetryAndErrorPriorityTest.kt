package ru.tusur.timetable.data.network

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.tusur.timetable.data.network.dto.LoadDto
import ru.tusur.timetable.data.network.dto.SearchDto
import ru.tusur.timetable.data.network.dto.WeekDto
import ru.tusur.timetable.domain.model.TimetableErrorKind
import java.io.IOException

/**
 * Ретраи и приоритет HTTP-кода над телом ответа. Найдено аудитом:
 * 404 ретраился, а 5xx с телом unknown_target заставлял выбирать группу заново.
 */
class NetworkRetryAndErrorPriorityTest {

    private fun httpError(code: Int, body: String? = null): HttpException =
        HttpException(
            Response.error<Any>(code, (body ?: "").toResponseBody("application/json".toMediaType())),
        )

    @Test
    fun `retries server errors and network failures`() = runTest {
        var calls = 0
        val result = runCatching {
            NetworkRetry.withRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw IOException("offline")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(3, calls)
    }

    @Test
    fun `does not retry client errors`() = runTest {
        var calls = 0
        runCatching {
            NetworkRetry.withRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw httpError(404, """{"error":"unknown_target"}""")
            }
        }
        assertEquals("404 не должен ретраиться", 1, calls)
    }

    @Test
    fun `does not retry bad week`() = runTest {
        var calls = 0
        runCatching {
            NetworkRetry.withRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw httpError(400, """{"error":"bad_week"}""")
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `retries rate limit`() = runTest {
        var calls = 0
        runCatching {
            NetworkRetry.withRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw httpError(429, """{"error":"rate_limited"}""")
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun `successful call stops retrying`() = runTest {
        var calls = 0
        val value = NetworkRetry.withRetry(attempts = 3, initialDelayMs = 1) {
            calls++
            if (calls < 2) throw IOException("flaky") else "ok"
        }
        assertEquals("ok", value)
        assertEquals(2, calls)
    }

    @Test
    fun `server error wins over unknown target body`() {
        val mapper = ApiErrorMapper(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })

        val error = mapper.map(httpError(503, """{"error":"unknown_target"}"""))

        assertEquals(TimetableErrorKind.UNAVAILABLE, error.kind)
        assertTrue("5xx должен ретраиться", error.isRetryable)
        assertTrue("Источник не пропал — выбирать заново не нужно", !error.isSourceGone)
    }

    @Test
    fun `unauthorized is not retried and not presented as raw code`() {
        val mapper = ApiErrorMapper(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })

        val error = mapper.map(httpError(401))

        assertTrue("401 не должен крутить бесконечные ретраи", !error.isRetryable)
        assertTrue(error.kind != TimetableErrorKind.UNKNOWN)
    }

    @Test
    fun `bad week without body is bad week`() {
        val mapper = ApiErrorMapper(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        assertEquals(TimetableErrorKind.BAD_WEEK, mapper.map(httpError(400)).kind)
    }

    @Test
    fun `unknown target on 404 still means source gone`() {
        val mapper = ApiErrorMapper(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        val error = mapper.map(httpError(404, """{"error":"unknown_target"}"""))
        assertEquals(TimetableErrorKind.UNKNOWN_TARGET, error.kind)
        assertTrue(error.isSourceGone)
    }

    @Test
    fun `official api empty payload is not an error`() = runTest {
        val api = mockk<OfficialTusurApi>()
        coEvery { api.lessons(any(), any(), any()) } returns ru.tusur.timetable.data.network.dto.OfficialLessonsResponse()
        coEvery { api.weeks(any()) } returns ru.tusur.timetable.data.network.dto.OfficialWeeksResponse()
        coEvery { api.lecturer(any(), any(), any()) } returns ru.tusur.timetable.data.network.dto.OfficialLessonsResponse()

        val source = OfficialRemoteSource(api)
        val week = source.fetchWeek(
            monday = java.time.LocalDate.of(2026, 11, 23),
            ref = ru.tusur.timetable.domain.model.SourceRef.group("fit/023"),
            fetchedAt = java.time.Instant.EPOCH,
        )

        assertEquals(7, week.days.size)
        assertEquals(0, week.totalLessons)
    }
}
