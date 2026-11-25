package ru.tusur.timetable.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.Week
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderPlannerTest {

    private val monday = LocalDate.of(2026, 11, 23)

    private fun lesson(date: LocalDate, start: Int, end: Int, pair: Int): Lesson = Lesson(
        position = pair,
        date = date,
        startMinutes = start,
        endMinutes = end,
        pair = pair,
        title = "Программирование ПЛИС",
        kindRaw = "Лабораторная работа",
        type = LessonType.LAB,
        place = "ф 331б",
        who = null,
    )

    private fun week(days: Map<LocalDate, List<Lesson>>): Week = Week(
        sourceKey = "group:fit/023",
        title = "гр. 023",
        isTeacher = false,
        monday = monday,
        today = monday.plusDays(2),
        parity = "нечётная",
        days = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            ScheduleDay(date, days[date].orEmpty())
        },
        sourceUrl = null,
        staleAt = null,
        fetchedAt = Instant.EPOCH,
    )

    private val defaultSettings = AppSettings(
        notificationsEnabled = true,
        beforeClassEnabled = true,
        beforeClassMinutes = 15,
        eveningDigestEnabled = false,
        morningDigestEnabled = false,
    )

    @Test
    fun `before class reminder triggers ahead of lesson`() {
        val date = LocalDate.of(2026, 11, 25)
        val data = week(mapOf(date to listOf(lesson(date, 16 * 60 + 45, 18 * 60 + 20, 5))))
        val now = LocalDateTime.of(2026, 11, 25, 10, 0)

        val planned = ReminderPlanner.plan(listOf(data), defaultSettings, now)

        assertEquals(1, planned.size)
        val reminder = planned.single()
        assertEquals(
            TomskTime.instantAt(date, 16 * 60 + 30).toEpochMilli(),
            reminder.triggerAtMillis,
        )
        assertTrue(reminder.content is ReminderPlanner.Content.BeforeClass)
        val content = reminder.content as ReminderPlanner.Content.BeforeClass
        assertEquals(15, content.minutesBefore)
        assertEquals("timetable://day/2026-11-25", reminder.deepLink)
    }

    @Test
    fun `past reminders are skipped`() {
        val date = LocalDate.of(2026, 11, 25)
        val data = week(mapOf(date to listOf(lesson(date, 16 * 60 + 45, 18 * 60 + 20, 5))))
        val now = LocalDateTime.of(2026, 11, 25, 17, 0)

        val planned = ReminderPlanner.plan(listOf(data), defaultSettings, now)
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `evening digest is for tomorrow`() {
        val tomorrow = LocalDate.of(2026, 11, 26)
        val data = week(mapOf(tomorrow to listOf(lesson(tomorrow, 13 * 60 + 15, 14 * 60 + 50, 3))))
        val now = LocalDateTime.of(2026, 11, 25, 18, 0)
        val settings = defaultSettings.copy(
            beforeClassEnabled = false,
            eveningDigestEnabled = true,
            eveningDigestHour = 19,
        )

        val planned = ReminderPlanner.plan(listOf(data), settings, now)

        assertEquals(1, planned.size)
        val content = planned.single().content as ReminderPlanner.Content.Digest
        assertTrue(content.forTomorrow)
        assertEquals(LocalDate.of(2026, 11, 26), content.date)
        assertEquals(1, content.lessons.size)
    }

    @Test
    fun `morning digest reports empty day`() {
        val today = LocalDate.of(2026, 11, 25)
        val data = week(emptyMap())
        val now = LocalDateTime.of(2026, 11, 25, 7, 0)
        val settings = defaultSettings.copy(
            beforeClassEnabled = false,
            morningDigestEnabled = true,
            morningDigestHour = 8,
        )

        val planned = ReminderPlanner.plan(listOf(data), settings, now)

        assertEquals(1, planned.size)
        val content = planned.single().content as ReminderPlanner.Content.Digest
        assertTrue(content.lessons.isEmpty())
        assertEquals(today, content.date)
    }

    @Test
    fun `disabled notifications produce nothing`() {
        val date = LocalDate.of(2026, 11, 25)
        val data = week(mapOf(date to listOf(lesson(date, 16 * 60 + 45, 18 * 60 + 20, 5))))
        val now = LocalDateTime.of(2026, 11, 25, 10, 0)

        val planned = ReminderPlanner.plan(
            listOf(data),
            defaultSettings.copy(notificationsEnabled = false),
            now,
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `same lesson yields stable notification id`() {
        val date = LocalDate.of(2026, 11, 25)
        val data = week(mapOf(date to listOf(lesson(date, 16 * 60 + 45, 18 * 60 + 20, 5))))
        val now = LocalDateTime.of(2026, 11, 25, 10, 0)

        val first = ReminderPlanner.plan(listOf(data), defaultSettings, now).single()
        val second = ReminderPlanner.plan(listOf(data), defaultSettings, now.plusMinutes(30)).single()

        assertEquals(first.notificationId, second.notificationId)
        assertEquals(first.eventKey, second.eventKey)
    }
}
