package ru.tusur.timetable.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableErrorKind
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственная точка перевода сетевых/HTTP-ошибок в человеческие состояния.
 * Сырые коды (`unknown_target` и т.п.) никогда не показываются в UI.
 */
@Singleton
class ApiErrorMapper @Inject constructor(
    private val json: Json,
) {

    fun map(throwable: Throwable): TimetableError = when (throwable) {
        is HttpException -> mapHttp(throwable)
        is SocketTimeoutException -> TimetableError(TimetableErrorKind.TIMEOUT)
        is UnknownHostException -> TimetableError(TimetableErrorKind.NETWORK)
        is IOException -> TimetableError(TimetableErrorKind.NETWORK)
        else -> TimetableError(TimetableErrorKind.UNKNOWN)
    }

    private fun mapHttp(exception: HttpException): TimetableError {
        val code = exception.code()
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val serverCode = parseServerCode(body)

        val kind = when {
            serverCode == "unknown_target" -> TimetableErrorKind.UNKNOWN_TARGET
            serverCode == "bad_week" -> TimetableErrorKind.BAD_WEEK
            serverCode == "rate_limited" || code == 429 -> TimetableErrorKind.RATE_LIMITED
            serverCode == "not_found" || code == 404 -> TimetableErrorKind.NOT_FOUND
            serverCode == "timetable_unavailable" || code >= 500 -> TimetableErrorKind.UNAVAILABLE
            code >= 500 -> TimetableErrorKind.UNAVAILABLE
            code == 400 -> TimetableErrorKind.BAD_WEEK
            else -> TimetableErrorKind.UNKNOWN
        }
        return TimetableError(kind, code)
    }

    private fun parseServerCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val element = json.parseToJsonElement(body)
            (element as? JsonObject)?.get("error")?.jsonPrimitive?.content
        }.getOrNull()
    }
}
