package ru.tusur.timetable.domain.model

/**
 * Ошибки API. Пользователю показываем человеческий текст и действие,
 * сырые коды наружу не выводим.
 */
enum class TimetableErrorKind {
    UNKNOWN_TARGET,
    BAD_WEEK,
    RATE_LIMITED,
    NOT_FOUND,
    UNAVAILABLE,
    NETWORK,
    TIMEOUT,
    MAPPING,
    UNKNOWN,
}

data class TimetableError(
    val kind: TimetableErrorKind,
    val httpCode: Int? = null,
) {
    /** Можно ли повторить запрос: обновить / подождать. */
    val isRetryable: Boolean
        get() = kind in setOf(
            TimetableErrorKind.NETWORK,
            TimetableErrorKind.TIMEOUT,
            TimetableErrorKind.UNAVAILABLE,
            TimetableErrorKind.RATE_LIMITED,
            TimetableErrorKind.UNKNOWN,
            TimetableErrorKind.NOT_FOUND,
        )

    /** Нужно выбрать расписание заново (источника больше нет). */
    val isSourceGone: Boolean
        get() = kind == TimetableErrorKind.UNKNOWN_TARGET
}

class TimetableException(
    val error: TimetableError,
    cause: Throwable? = null,
) : Exception("timetable:${error.kind}", cause)

fun Throwable.toTimetableError(): TimetableError =
    (this as? TimetableException)?.error ?: TimetableError(TimetableErrorKind.UNKNOWN)
