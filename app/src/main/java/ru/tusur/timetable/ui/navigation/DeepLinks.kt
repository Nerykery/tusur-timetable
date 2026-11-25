package ru.tusur.timetable.ui.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Deep links приложения:
 *  - timetable://day/2026-11-24
 *  - timetable://group/fit/023
 *  - timetable://teacher/35663
 *  - timetable://map?r=331%D0%B1
 *
 * Разбор на java.net.URI, чтобы логика жила в JVM-тестах без Android.
 */
sealed interface AppLink {
    data class Day(val date: String?) : AppLink

    data class Source(val key: String, val date: String? = null) : AppLink

    data class Map(val room: String?) : AppLink
}

object DeepLinks {

    private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")

    fun parse(raw: String?): AppLink? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null

        val segments = uri.path.orEmpty()
            .split('/')
            .filter { it.isNotBlank() }

        return when (uri.host?.lowercase()) {
            "day" -> AppLink.Day(segments.firstOrNull { datePattern.matches(it) })

            "group" -> {
                // Дату в пути отделяем от группы: /group/fit/023/2026-11-24.
                val dateSegment = segments.lastOrNull { datePattern.matches(it) }
                val groupSegments = segments.filter { !datePattern.matches(it) }
                if (groupSegments.isEmpty()) {
                    null
                } else {
                    AppLink.Source(
                        KEY_GROUP + groupSegments.joinToString("/"),
                        dateSegment ?: queryParam(uri, "date")?.validDate(),
                    )
                }
            }

            "teacher" -> segments.firstOrNull { !datePattern.matches(it) }
                ?.let { AppLink.Source(KEY_TEACHER + it, queryParam(uri, "date")?.validDate()) }

            "map" -> AppLink.Map(
                queryParam(uri, "r") ?: segments.firstOrNull(),
            )

            else -> null
        }
    }

    fun routeFor(link: AppLink): String = when (link) {
        is AppLink.Day -> if (link.date != null) {
            "schedule?date=${encode(link.date)}"
        } else {
            ROUTE_SCHEDULE
        }

        is AppLink.Source -> buildString {
            append(ROUTE_SCHEDULE)
            append("?source=")
            append(encode(link.key))
            link.date?.let {
                append("&date=")
                append(encode(it))
            }
        }

        is AppLink.Map -> link.room?.let { "map?room=${encode(it)}" } ?: ROUTE_MAP
    }

    private fun queryParam(uri: URI, name: String): String? {
        val query = uri.rawQuery ?: return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }
            ?.let(::decode)
    }

    /** Даты из ссылки принимаем только в формате ISO, остальное — «сегодня». */
    private fun String.validDate(): String? = takeIf { datePattern.matches(it) }

    private fun decode(value: String): String? =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()

    private fun encode(value: String): String =
        runCatching { URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)

    const val ROUTE_SCHEDULE = "schedule"
    const val ROUTE_MAP = "map"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_ABOUT = "about"

    private const val SCHEME = "timetable"
    private const val KEY_GROUP = "group:"
    private const val KEY_TEACHER = "teacher:"
}
