package ru.tusur.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tusur.timetable.data.mapper.OfficialWeekMapper
import ru.tusur.timetable.data.network.dto.OfficialDayDto
import ru.tusur.timetable.data.network.dto.OfficialLessonDto
import ru.tusur.timetable.data.network.dto.OfficialLecturerDto
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.SourceRef
import java.time.Instant
import java.time.LocalDate

class OfficialWeekMapperTest {

    private val monday = LocalDate.of(2026, 11, 23)

    private fun lesson(
        number: Int,
        start: String,
        end: String,
        classroom: String,
        groups: String,
    ) = OfficialLessonDto(
        day = 1,
        number = number,
        name = "Программирование ПЛИС",
        abbr = "ППЛИС",
        kind = "Лабораторная работа",
        startTime = start,
        endTime = end,
        classroom = classroom,
        groups = groups,
        lecturers = listOf(
            OfficialLecturerDto(35612, "Мария", "Алексеевна", "Костина"),
        ),
    )

    @Test
    fun `group week maps classroom, lecturer and map room`() {
        val week = OfficialWeekMapper.map(
            days = listOf(
                OfficialDayDto(
                    date = "2026-11-23",
                    lessons = listOf(lesson(5, "16:45", "18:20", "ф 331б", "023")),
                ),
            ),
            ref = SourceRef.group("fit/023", "гр. 023"),
            monday = monday,
            parity = "odd",
            fetchedAt = Instant.EPOCH,
            today = LocalDate.of(2026, 11, 23),
        )

        val mapped = week.days.first { it.date == monday }.lessons.single()
        assertEquals(LessonType.LAB, mapped.type)
        assertEquals("331б", mapped.mapRoom)
        assertEquals("Костина М.А.", mapped.who)
        assertEquals("teacher:35612", mapped.people.single().sourceKey)
        assertEquals("нечётная", week.parity)
    }

    @Test
    fun `teacher week exposes groups from groups string`() {
        val week = OfficialWeekMapper.map(
            days = listOf(
                OfficialDayDto(
                    date = "2026-11-23",
                    lessons = listOf(lesson(3, "13:15", "14:50", "ауд. 220", "013, 023")),
                ),
            ),
            ref = SourceRef.teacher("35612", "Костина Мария Алексеевна"),
            monday = monday,
            parity = null,
            fetchedAt = Instant.EPOCH,
            today = LocalDate.of(2026, 11, 23),
        )

        val mapped = week.days.first { it.date == monday }.lessons.single()
        assertEquals(2, mapped.people.size)
        assertEquals("group:013", mapped.people.first().sourceKey)
        // Вне корпуса ФЭТ карта не строится.
        assertEquals(null, mapped.mapRoom)
        assertEquals(null, week.parityLabel)
    }

    @Test
    fun `missing days are filled to full week`() {
        val week = OfficialWeekMapper.map(
            days = emptyList(),
            ref = SourceRef.group("fit/023"),
            monday = monday,
            parity = "even",
            fetchedAt = Instant.EPOCH,
            today = LocalDate.of(2026, 11, 23),
        )
        assertEquals(7, week.days.size)
        assertEquals(0, week.totalLessons)
    }
}
