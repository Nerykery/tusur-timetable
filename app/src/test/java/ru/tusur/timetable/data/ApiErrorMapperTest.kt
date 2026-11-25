package ru.tusur.timetable.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.tusur.timetable.data.network.ApiErrorMapper
import ru.tusur.timetable.domain.model.TimetableErrorKind
import java.io.IOException
import java.net.SocketTimeoutException

class ApiErrorMapperTest {

    private val mapper = ApiErrorMapper(
        Json { ignoreUnknownKeys = true },
    )

    private fun httpError(code: Int, body: String?): HttpException {
        val response = Response.error<Any>(
            code,
            (body ?: "").toResponseBody("application/json".toMediaType()),
        )
        return HttpException(response)
    }

    @Test
    fun `unknown target maps to human state`() {
        val error = mapper.map(httpError(404, """{"error":"unknown_target"}"""))
        assertEquals(TimetableErrorKind.UNKNOWN_TARGET, error.kind)
        assertEquals(404, error.httpCode)
    }

    @Test
    fun `bad week maps to bad week`() {
        assertEquals(
            TimetableErrorKind.BAD_WEEK,
            mapper.map(httpError(400, """{"error":"bad_week"}""")).kind,
        )
    }

    @Test
    fun `rate limit maps to rate limited`() {
        assertEquals(
            TimetableErrorKind.RATE_LIMITED,
            mapper.map(httpError(429, """{"error":"rate_limited"}""")).kind,
        )
    }

    @Test
    fun `server error maps to unavailable`() {
        assertEquals(
            TimetableErrorKind.UNAVAILABLE,
            mapper.map(httpError(503, """{"error":"timetable_unavailable"}""")).kind,
        )
    }

    @Test
    fun `plain 404 without body still resolves`() {
        assertEquals(
            TimetableErrorKind.NOT_FOUND,
            mapper.map(httpError(404, null)).kind,
        )
    }

    @Test
    fun `network errors map without http code`() {
        assertEquals(TimetableErrorKind.NETWORK, mapper.map(IOException("offline")).kind)
        assertEquals(TimetableErrorKind.TIMEOUT, mapper.map(SocketTimeoutException()).kind)
    }
}
