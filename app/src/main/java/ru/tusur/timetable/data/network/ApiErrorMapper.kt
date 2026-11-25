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

        // HTTP-код старше тела: упавший прокси может вернуть 5xx с телом unknown_target,
        // и «выберите группу заново» будет ложью.
        val kind = when {
            code >= 500 || serverCode == "timetable_unavailable" -> TimetableErrorKind.UNAVAILABLE
            code == 429 || serverCode == "rate_limited" -> TimetableErrorKind.RATE_LIMITED
            serverCode == "unknown_target" -> TimetableErrorKind.UNKNOWN_TARGET
            serverCode == "bad_week" || code == 400 -> TimetableErrorKind.BAD_WEEK
            serverCode == "not_found" || code == 404 -> TimetableErrorKind.NOT_FOUND
            code == 401 || code == 403 -> TimetableErrorKind.NOT_FOUND
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
