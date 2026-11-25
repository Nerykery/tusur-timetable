package ru.tusur.timetable.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** Регрессии планировщика напоминаний, найденные аудитом. */
class ReminderPlannerRegressionTest {

    private val monday = LocalDate.of(2026, 11, 23)
    private val today = LocalDate.of(2026, 11, 25)

    private fun lesson(date: LocalDate, start: Int, end: Int, pair: Int, title: String = "Пара") = Lesson(
        position = pair,
        date = date,
        startMinutes = start,
        endMinutes = end,
        pair = pair,
        title = title,
        kindRaw = "Лекция",
        type = LessonType.LECTURE,
        place = "ф 331б",
        who = null,
    )

    private fun week(days: Map<LocalDate, List<Lesson>>): Week = Week(
        sourceKey = "group:fit/023",
        title = "гр. 023",
        isTeacher = false,
        monday = monday,
        today = today,
        parity = "нечётная",
        days = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            ScheduleDay(date, days[date].orEmpty())
        },
        sourceUrl = null,
        staleAt = null,
        fetchedAt = Instant.EPOCH,
    )

    private val settings = AppSettings(
        notificationsEnabled = true,
        beforeClassEnabled = true,
        beforeClassMinutes = 15,
    )

    @Test
    fun `two lessons with same time and pair both get reminders`() {
        // Подгруппы: две разные пары в один слот (например, английский и немецкий).
        val data = week(
            mapOf(
                today to listOf(
                    lesson(today, 16 * 60 + 45, 18 * 60 + 20, 5, "Английский"),
                    lesson(today, 16 * 60 + 45, 18 * 60 + 20, 5, "Немецкий"),
                ),
            ),
        )

        val planned = ReminderPlanner.plan(listOf(data), settings, today.atTime(10, 0))

        assertEquals("Оба напоминания должны выжить", 2, planned.size)
        assertEquals(
            2,
            planned.map { it.notificationId }.distinct().size,
        )
        val titles = planned.mapNotNull { (it.content as? ReminderPlanner.Content.BeforeClass)?.title }
        assertTrue("Должны быть обе дисциплины", titles.containsAll(listOf("Английский", "Немецкий")))
    }

    @Test
    fun `lesson just after midnight fires the previous evening`() {
        val nightLessonDate = today.plusDays(1)
        val data = week(mapOf(nightLessonDate to listOf(lesson(nightLessonDate, 10, 95, 1, "Ночной экзамен"))))

        val planned = ReminderPlanner.plan(
            listOf(data),
            settings.copy(beforeClassMinutes = 15),
            today.atTime(12, 0),
        )

        val reminder = planned.single()
        val expected = TomskTime.instantAt(today, 23 * 60 + 55).toEpochMilli()
        assertEquals("Напоминание должно уйти накануне в 23:55", expected, reminder.triggerAtMillis)
    }

    @Test
    fun `evening digest marks missing data instead of claiming no lessons`() {
        // В кэше только текущая неделя, на завтра данных нет.
        val data = week(mapOf(today to listOf(lesson(today, 13 * 60 + 15, 14 * 60 + 50, 3))))
        val evening = settings.copy(
            beforeClassEnabled = false,
            eveningDigestEnabled = true,
            eveningDigestHour = 19,
        )

        // Воскресенье 29.11: завтра 30.11 — уже следующая неделя, её в кэше нет.
        val planned = ReminderPlanner.plan(listOf(data), evening, LocalDate.of(2026, 11, 29).atTime(18, 0))
        assertEquals(1, planned.size)
        val content = planned.single().content as ReminderPlanner.Content.Digest
        assertTrue("Нельзя утверждать, что пар нет, если неделя не загружена", content.dataMissing)
        assertTrue(content.lessons.isEmpty())
    }

    @Test
    fun `evening digest with known empty day says no lessons`() {
        val data = week(emptyMap())
        val evening = settings.copy(
            beforeClassEnabled = false,
            eveningDigestEnabled = true,
            eveningDigestHour = 19,
        )

        val planned = ReminderPlanner.plan(listOf(data), evening, LocalDate.of(2026, 11, 27).atTime(18, 0))

        val content = planned.single().content as ReminderPlanner.Content.Digest
        assertFalse("Неделя загружена, день действительно пустой", content.dataMissing)
        assertTrue(content.lessons.isEmpty())
    }

    @Test
    fun `lesson starting inside the lead window is skipped`() {
        val now = today.atTime(16, 30)
        val data = week(
            mapOf(
                today to listOf(
                    // Ровно в окне (16:45 - 15 мин = 16:30) — уже поздно.
                    lesson(today, 16 * 60 + 45, 18 * 60 + 20, 5, "Слишком поздно"),
                ),
            ),
        )

        val planned = ReminderPlanner.plan(listOf(data), settings, now)

        assertTrue(planned.isEmpty())
    }

    @Test
    fun `lesson outside the lead window still gets a reminder`() {
        val now = today.atTime(16, 20)
        val data = week(mapOf(today to listOf(lesson(today, 16 * 60 + 45, 18 * 60 + 20, 5))))

        val planned = ReminderPlanner.plan(listOf(data), settings, now)

        assertEquals(1, planned.size)
    }
}
