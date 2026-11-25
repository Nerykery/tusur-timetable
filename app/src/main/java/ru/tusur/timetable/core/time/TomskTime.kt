package ru.tusur.timetable.core.time

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/**
 * Время ТУСУР — UTC+7 (Asia/Tomsk). Все вычисления «сегодня», «сейчас» и «идёт пара»
 * делаются в этом поясе, независимо от часового пояса устройства.
 */
object TomskTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Tomsk")

    /** Окно запроса недель: ±30 недель от текущей. */
    const val WEEKS_WINDOW: Long = 30

    const val MINUTES_IN_DAY: Int = 24 * 60

    fun now(clock: Clock = Clock.systemUTC()): LocalDateTime = LocalDateTime.now(clock.withZone(ZONE))

    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(ZONE))

    fun nowMinutes(clock: Clock = Clock.systemUTC()): Int =
        now(clock).let { it.hour * 60 + it.minute }

    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SUNDAY

    fun isWithinWindow(monday: LocalDate, today: LocalDate): Boolean =
        abs(ChronoUnit.WEEKS.between(mondayOf(today), monday)) <= WEEKS_WINDOW

    fun parseDate(raw: String?): LocalDate? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun parseTimeToMinutes(raw: String?): Int? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { LocalTime.parse(value) }.getOrNull() ?: return null
        return parsed.hour * 60 + parsed.minute
    }

    fun formatMinutes(minutes: Int): String {
        val normalized = ((minutes % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY
        return String.format(Locale.ROOT, "%02d:%02d", normalized / 60, normalized % 60)
    }

    fun formatTime(raw: String?): String =
        parseTimeToMinutes(raw)?.let(::formatMinutes) ?: raw.orEmpty()

    /** 75 -> "1 ч 15 мин", 25 -> "25 мин", 120 -> "2 ч". */
    fun formatDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours == 0 -> "$mins мин"
            mins == 0 -> "$hours ч"
            else -> "$hours ч $mins мин"
        }
    }

    /** "24–30 ноября" или "30 ноября – 6 декабря". */
    fun formatRange(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        val endMonth = monthName(sunday.month)
        return if (monday.month == sunday.month) {
            "${monday.dayOfMonth}–${sunday.dayOfMonth} $endMonth"
        } else {
            "${monday.dayOfMonth} ${monthName(monday.month)} – ${sunday.dayOfMonth} $endMonth"
        }
    }

    fun formatHumanDate(date: LocalDate): String = "${date.dayOfMonth} ${monthName(date.month)}"

    fun formatFullDate(date: LocalDate): String =
        "${weekdayShort(date)}, ${date.dayOfMonth} ${monthName(date.month)}"

    fun monthName(month: Month): String = when (month) {
        Month.JANUARY -> "января"
        Month.FEBRUARY -> "февраля"
        Month.MARCH -> "марта"
        Month.APRIL -> "апреля"
        Month.MAY -> "мая"
        Month.JUNE -> "июня"
        Month.JULY -> "июля"
        Month.AUGUST -> "августа"
        Month.SEPTEMBER -> "сентября"
        Month.OCTOBER -> "октября"
        Month.NOVEMBER -> "ноября"
        Month.DECEMBER -> "декабря"
    }

    fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "пн"
        DayOfWeek.TUESDAY -> "вт"
        DayOfWeek.WEDNESDAY -> "ср"
        DayOfWeek.THURSDAY -> "чт"
        DayOfWeek.FRIDAY -> "пт"
        DayOfWeek.SATURDAY -> "сб"
        DayOfWeek.SUNDAY -> "вс"
    }

    fun weekdayFull(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "понедельник"
        DayOfWeek.TUESDAY -> "вторник"
        DayOfWeek.WEDNESDAY -> "среда"
        DayOfWeek.THURSDAY -> "четверг"
        DayOfWeek.FRIDAY -> "пятница"
        DayOfWeek.SATURDAY -> "суббота"
        DayOfWeek.SUNDAY -> "воскресенье"
    }

    fun at(date: LocalDate, minutes: Int): LocalDateTime {
        val normalized = minutes.coerceIn(0, MINUTES_IN_DAY - 1)
        return date.atStartOfDay().plusMinutes(normalized.toLong())
    }

    fun instantAt(date: LocalDate, minutes: Int): Instant = at(date, minutes).atZone(ZONE).toInstant()

    /** Индекс дня для таблицы загрузки корпуса: Пн=0 … Сб=5, Вс=6. */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1
}
