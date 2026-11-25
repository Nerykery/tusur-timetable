package ru.tusur.timetable.domain.model

import ru.tusur.timetable.core.time.TomskTime
import java.time.Instant
import java.time.LocalDate

data class ScheduleDay(
    val date: LocalDate,
    val lessons: List<Lesson>,
) {
    val hasLessons: Boolean get() = lessons.isNotEmpty()
    val lessonCount: Int get() = lessons.size
}

data class Week(
    val sourceKey: String,
    val title: String,
    val isTeacher: Boolean,
    val monday: LocalDate,
    val today: LocalDate?,
    val parity: String?,
    val days: List<ScheduleDay>,
    val sourceUrl: String?,
    val staleAt: String?,
    val fetchedAt: Instant,
) {
    val daysCount: Int get() = days.size
    val totalLessons: Int get() = days.sumOf { it.lessonCount }
    val stale: Boolean get() = !staleAt.isNullOrBlank()

    /** «нечётная неделя» — для подзаголовка главного экрана. */
    val parityLabel: String?
        get() = parity?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            if (value.contains("нед", ignoreCase = true)) value else "$value неделя"
        }

    fun day(date: LocalDate): ScheduleDay? = days.firstOrNull { it.date == date }

    fun lessonsAt(date: LocalDate): List<Lesson> = day(date)?.lessons.orEmpty()

    /** Ближайший день недели (включая текущий), где есть пары. */
    fun nearestDayWithLessons(from: LocalDate): ScheduleDay? {
        val ordered = days.sortedBy { it.date }
        return ordered.firstOrNull { !it.date.isBefore(from) && it.hasLessons }
            ?: ordered.lastOrNull { it.hasLessons }
    }
}

data class DayStatus(
    val live: Lesson?,
    val next: Lesson?,
    val minutesLeft: Int?,
    val minutesToNext: Int?,
    val allFinished: Boolean,
    val isEmpty: Boolean,
) {
    val hasLessons: Boolean get() = !isEmpty
}

/**
 * «Идёт N-я пара», «Следующая через …», «Пары закончились» — общая логика
 * для главного экрана и виджетов.
 */
fun computeDayStatus(lessons: List<Lesson>, nowMinutes: Int): DayStatus {
    val sorted = lessons.sortedBy { it.startMinutes }
    if (sorted.isEmpty()) {
        return DayStatus(null, null, null, null, allFinished = false, isEmpty = true)
    }
    val live = sorted.firstOrNull { nowMinutes >= it.startMinutes && nowMinutes < it.endMinutes }
    val next = sorted.firstOrNull { it.startMinutes > nowMinutes }
    val finished = live == null && next == null
    return DayStatus(
        live = live,
        next = next,
        minutesLeft = live?.let { it.endMinutes - nowMinutes },
        minutesToNext = next?.let { it.startMinutes - nowMinutes },
        allFinished = finished,
        isEmpty = false,
    )
}

/** Чётность недели: сервер отдаёт «чётная»/«нечётная», официальный API — odd/even. */
fun parityLabelFrom(raw: String?): String? {
    val value = raw?.trim()?.lowercase().orEmpty()
    return when {
        value.isEmpty() -> null
        value.startsWith("odd") || value.startsWith("неч") -> "нечётная"
        value.startsWith("even") || value.startsWith("чёт") || value.startsWith("чет") -> "чётная"
        else -> raw?.trim()
    }
}

/** Понедельник недели, в которой лежит дата; сервер нормализует любую дату к понедельнику. */
fun mondayOf(date: LocalDate): LocalDate = TomskTime.mondayOf(date)
