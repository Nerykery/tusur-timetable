package ru.tusur.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.mapper.ProxyResponseMapper
import ru.tusur.timetable.data.mapper.toLesson
import ru.tusur.timetable.data.network.dto.EventDto
import ru.tusur.timetable.data.network.dto.LoadDto
import ru.tusur.timetable.data.network.dto.RoomDto
import ru.tusur.timetable.data.network.dto.BusyDto
import ru.tusur.timetable.data.network.dto.DayDto
import ru.tusur.timetable.data.network.dto.WeekDto
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.domain.model.TimetableException
import java.time.Instant
import java.time.LocalDate

/**
 * Регрессии, найденные аудитом: молчаливая потеря пар, короткая неделя,
 * исчезающая занятость аудиторий.
 */
class MapperRegressionTest {

    private val mapper = ProxyResponseMapper()
    private val fetchedAt = Instant.parse("2026-11-25T10:00:00Z")
    private val monday = LocalDate.of(2026, 11, 23)
    private val ref = SourceRef.group("fit/023")

    @Test
    fun `single digit hour is parsed instead of dropping the lesson`() {
        assertEquals(8 * 60 + 50, TomskTime.parseTimeToMinutes("8:50"))
        assertEquals(9 * 60 + 5, TomskTime.parseTimeToMinutes("9:05"))

        val lesson = EventDto(
            start = "8:50",
            end = "10:25",
            pair = 1,
            title = "Физика",
        ).toLesson(monday, 0)

        assertTrue("Пара не должна теряться", lesson != null)
        assertEquals(8 * 60 + 50, lesson!!.startMinutes)
    }

    @Test
    fun `placeholder time is rejected, not turned into midnight`() {
        assertEquals(null, TomskTime.parseTimeToMinutes("--:--"))
        assertEquals(null, TomskTime.parseTimeToMinutes(""))
        assertEquals(null, TomskTime.parseTimeToMinutes("25:99"))
    }

    @Test
    fun `week without monday becomes mapping error instead of infinite retry`() {
        val dto = WeekDto(title = "гр. 023", monday = "", days = emptyList())

        val failure = runCatching { mapper.mapWeek(dto, ref, fetchedAt) }.exceptionOrNull()

        val error = (failure as? TimetableException)?.error
        assertEquals(TimetableErrorKind.MAPPING, error?.kind)
        assertTrue("Ошибка разбора не должна ретраиться бесконечно", error?.isRetryable == false)
    }

    @Test
    fun `short week is padded to seven days`() {
        val dto = WeekDto(
            title = "гр. 023",
            monday = "2026-11-23",
            parity = "нечётная",
            days = listOf(
                DayDto(
                    date = "2026-11-23",
                    events = listOf(EventDto(start = "16:45", end = "18:20", pair = 5, title = "ПЛИС")),
                ),
            ),
        )

        val week = mapper.mapWeek(dto, ref, fetchedAt)

        assertEquals(7, week.days.size)
        assertEquals(monday.plusDays(6), week.days.last().date)
        assertEquals(1, week.totalLessons)
        assertTrue(week.days[5].lessons.isEmpty())
    }

    @Test
    fun `load without pairs grid is rejected instead of misindexing`() {
        val dto = LoadDto(
            monday = "2026-11-23",
            parity = "нечётная",
            pairs = emptyList(),
            rooms = mapOf("331b" to RoomDto(busy = mapOf("3" to listOf(BusyDto(t = "ПЛИС"))))),
        )

        val failure = runCatching { mapper.mapLoad(dto) }.exceptionOrNull()

        assertEquals(TimetableErrorKind.MAPPING, (failure as? TimetableException)?.error?.kind)
    }

    @Test
    fun `load with full grid maps busy cells by day and pair`() {
        val pairs = (0..6).map { listOf("08:50", "10:25") }
        val dto = LoadDto(
            monday = "2026-11-23",
            parity = "нечётная",
            pairs = pairs,
            rooms = mapOf(
                "331b" to RoomDto(
                    href = "https://timetable.tusur.ru/buildings/f/auditoriums/331b",
                    busy = mapOf("2" to listOf(BusyDto(t = "ПЛИС", g = "023", w = "Костина М.А."))),
                ),
            ),
        )

        val load = mapper.mapLoad(dto)

        assertEquals(7, load.pairs.size)
        assertEquals(1, load.lessons("331б", dayIndex = 0, pairIndex = 2)?.size)
        assertEquals(0, load.lessons("331б", dayIndex = 0, pairIndex = 3)?.size)
        // Пустая ячейка другого дня не считается занятой.
        assertEquals(0, load.lessons("331б", dayIndex = 2, pairIndex = 2)?.size)
    }
}
