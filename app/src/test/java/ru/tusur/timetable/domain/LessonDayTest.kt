package ru.tusur.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.domain.model.BREAK_MAX_MINUTES
import ru.tusur.timetable.domain.model.DayStatus
import ru.tusur.timetable.domain.model.GapKind
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonPhase
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.TimelineItem
import ru.tusur.timetable.domain.model.buildTimeline
import ru.tusur.timetable.domain.model.computeDayStatus
import java.time.LocalDate

class LessonDayTest {

    private val date = LocalDate.of(2026, 11, 24)

    private fun lesson(
        start: Int,
        end: Int,
        pair: Int = 1,
        title: String = "Дисциплина",
    ) = Lesson(
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

    @Test
    fun `lesson kind maps by substring`() {
        assertEquals(LessonType.LECTURE, LessonType.fromKind("Лекция"))
        assertEquals(LessonType.LECTURE, LessonType.fromKind("Лекции"))
        assertEquals(LessonType.PRACTICE, LessonType.fromKind("Практика"))
        assertEquals(LessonType.LAB, LessonType.fromKind("Лабораторная работа"))
        assertEquals(LessonType.COURSEWORK, LessonType.fromKind("Курсовая работа"))
        assertEquals(LessonType.EXAM, LessonType.fromKind("Экзамен"))
        assertEquals(LessonType.EXAM, LessonType.fromKind("Зачёт"))
        assertEquals(LessonType.CONSULTATION, LessonType.fromKind("Консультация"))
        assertEquals(LessonType.OTHER, LessonType.fromKind(""))
        assertEquals(LessonType.OTHER, LessonType.fromKind(null))
    }

    @Test
    fun `short gap is break and long gap is window`() {
        val lessons = listOf(
            lesson(8 * 60 + 50, 10 * 60 + 25, pair = 1, title = "Первая"),
            lesson(10 * 60 + 40, 12 * 60 + 15, pair = 2, title = "Вторая"),
            lesson(13 * 60 + 15, 14 * 60 + 50, pair = 3, title = "Третья"),
        )
        val timeline = buildTimeline(lessons)
        val gaps = timeline.filterIsInstance<TimelineItem.GapItem>()

        assertEquals(2, gaps.size)
        assertEquals(GapKind.BREAK, gaps[0].kind)
        assertEquals(15, gaps[0].duration)
        assertEquals(GapKind.WINDOW, gaps[1].kind)
        assertEquals(60, gaps[1].duration)
        assertTrue(gaps[0].duration <= BREAK_MAX_MINUTES)
    }

    @Test
    fun `live lesson wins over next`() {
        val lessons = listOf(
            lesson(8 * 60 + 50, 10 * 60 + 25, pair = 1),
            lesson(10 * 60 + 40, 12 * 60 + 15, pair = 2),
        )
        val status = computeDayStatus(lessons, 9 * 60)

        assertTrue(status.live != null)
        assertEquals(85, status.minutesLeft)
        assertEquals(100, status.minutesToNext)
        assertFalse(status.allFinished)
    }

    @Test
    fun `between lessons next is scheduled`() {
        val lessons = listOf(
            lesson(8 * 60 + 50, 10 * 60 + 25, pair = 1),
            lesson(10 * 60 + 40, 12 * 60 + 15, pair = 2),
        )
        val status = computeDayStatus(lessons, 10 * 60 + 30)

        assertEquals(null, status.live)
        assertEquals(10, status.minutesToNext)
        assertFalse(status.allFinished)
    }

    @Test
    fun `after last lesson day is finished`() {
        val lessons = listOf(lesson(8 * 60 + 50, 10 * 60 + 25))
        val status = computeDayStatus(lessons, 12 * 60)

        assertTrue(status.allFinished)
        assertEquals(null, status.live)
        assertEquals(null, status.next)
    }

    @Test
    fun `empty day is empty`() {
        val status = computeDayStatus(emptyList(), 10 * 60)
        assertTrue(status.isEmpty)
        assertFalse(status.allFinished)
        assertEquals(DayStatus(null, null, null, null, false, true), status)
    }

    @Test
    fun `status for the whole day on late evening`() {
        val lessons = listOf(
            lesson(13 * 60 + 15, 14 * 60 + 50, pair = 3),
            lesson(15 * 60, 16 * 60 + 35, pair = 4),
        )
        val status = computeDayStatus(lessons, 23 * 60)
        assertTrue(status.allFinished)
        assertFalse(status.isEmpty)
    }

    @Test
    fun `lesson phase boundaries`() {
        val item = lesson(100, 160)
        assertEquals(LessonPhase.Upcoming, item.phaseAt(99))
        val live = item.phaseAt(130) as LessonPhase.Live
        assertEquals(30, live.minutesLeft)
        assertEquals(0.5f, live.progress, 0.001f)
        assertEquals(LessonPhase.Past, item.phaseAt(160))
    }
}
