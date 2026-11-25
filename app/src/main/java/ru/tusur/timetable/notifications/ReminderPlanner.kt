package ru.tusur.timetable.notifications

import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.Week
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планировщик напоминаний: чистая функция без Android-зависимостей.
 * Возвращает только будущие срабатывания; одно событие — один стабильный id,
 * поэтому повторная пересборка не плодит дубли.
 */
object ReminderPlanner {

    data class DigestLesson(
        val startLabel: String,
        val title: String,
        val place: String?,
    )

    sealed interface Content {
        data class BeforeClass(
            val date: LocalDate,
            val pair: Int?,
            val startLabel: String,
            val title: String,
            val place: String?,
            val minutesBefore: Int,
        ) : Content

        data class Digest(
            val date: LocalDate,
            val lessons: List<DigestLesson>,
            val forTomorrow: Boolean,
        ) : Content
    }

    data class PlannedReminder(
        val notificationId: Int,
        val requestCode: Int,
        val triggerAtMillis: Long,
        val channel: String,
        val eventKey: String,
        val deepLink: String,
        val content: Content,
    )

    fun plan(
        weeks: List<Week>,
        settings: AppSettings,
        now: LocalDateTime,
        zone: ZoneId = TomskTime.ZONE,
    ): List<PlannedReminder> {
        if (!settings.notificationsEnabled) return emptyList()

        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val today = now.toLocalDate()
        val lessonsByDate: Map<LocalDate, List<Lesson>> = weeks
            .flatMap { it.days }
            .associate { day -> day.date to day.lessons }

        val result = LinkedHashMap<Int, PlannedReminder>()

        fun add(reminder: PlannedReminder) {
            if (reminder.triggerAtMillis <= nowMillis + MIN_LEAD_MILLIS) return
            result[reminder.notificationId] = reminder
        }

        if (settings.beforeClassEnabled) {
            for (offset in 0L..1L) {
                val date = today.plusDays(offset)
                lessonsByDate[date].orEmpty().forEach { lesson ->
                    val startMinutes = lesson.startMinutes - settings.beforeClassMinutes
                    val trigger = TomskTime.instantAt(date, startMinutes).toEpochMilli()
                    val eventKey = "before:$date:${lesson.startMinutes}:${lesson.pair ?: lesson.position}"
                    add(
                        PlannedReminder(
                            notificationId = stableId(eventKey),
                            requestCode = stableId(eventKey),
                            triggerAtMillis = trigger,
                            channel = NotificationChannels.REMINDERS,
                            eventKey = eventKey,
                            deepLink = "timetable://day/$date",
                            content = Content.BeforeClass(
                                date = date,
                                pair = lesson.pair,
                                startLabel = lesson.startLabel,
                                title = lesson.title,
                                place = lesson.place,
                                minutesBefore = settings.beforeClassMinutes,
                            ),
                        ),
                    )
                }
            }
        }

        if (settings.morningDigestEnabled) {
            val trigger = TomskTime.instantAt(today, settings.morningDigestHour * 60).toEpochMilli()
            val eventKey = "morning:$today"
            add(
                PlannedReminder(
                    notificationId = stableId(eventKey),
                    requestCode = stableId(eventKey),
                    triggerAtMillis = trigger,
                    channel = NotificationChannels.DIGEST,
                    eventKey = eventKey,
                    deepLink = "timetable://day/$today",
                    content = Content.Digest(
                        date = today,
                        lessons = digestLessons(lessonsByDate[today]),
                        forTomorrow = false,
                    ),
                ),
            )
        }

        if (settings.eveningDigestEnabled) {
            val tomorrow = today.plusDays(1)
            val trigger = TomskTime.instantAt(today, settings.eveningDigestHour * 60).toEpochMilli()
            val eventKey = "evening:$today"
            add(
                PlannedReminder(
                    notificationId = stableId(eventKey),
                    requestCode = stableId(eventKey),
                    triggerAtMillis = trigger,
                    channel = NotificationChannels.DIGEST,
                    eventKey = eventKey,
                    deepLink = "timetable://day/$tomorrow",
                    content = Content.Digest(
                        date = tomorrow,
                        lessons = digestLessons(lessonsByDate[tomorrow]),
                        forTomorrow = true,
                    ),
                ),
            )
        }

        return result.values.sortedBy { it.triggerAtMillis }
    }

    private fun digestLessons(lessons: List<Lesson>?): List<DigestLesson> =
        lessons.orEmpty()
            .sortedBy { it.startMinutes }
            .take(MAX_DIGEST_LESSONS)
            .map {
                DigestLesson(
                    startLabel = it.startLabel,
                    title = it.title,
                    place = it.place,
                )
            }

    private fun stableId(eventKey: String): Int {
        val hash = eventKey.hashCode() and Int.MAX_VALUE
        return if (hash == 0) 1 else hash
    }

    private const val MIN_LEAD_MILLIS = 5_000L
    private const val MAX_DIGEST_LESSONS = 5
}
