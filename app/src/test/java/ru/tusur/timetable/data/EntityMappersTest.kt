package ru.tusur.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.data.db.toDomain
import ru.tusur.timetable.data.db.toEventEntities
import ru.tusur.timetable.data.db.toPersonEntities
import ru.tusur.timetable.data.db.toWeekEntity
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.Week
import java.time.Instant
import java.time.LocalDate

class EntityMappersTest {

    private val monday = LocalDate.of(2026, 11, 23)

    private fun week(): Week {
        val lesson = Lesson(
            position = 0,
            date = monday,
            startMinutes = 16 * 60 + 45,
            endMinutes = 18 * 60 + 20,
            pair = 5,
            title = "Программирование ПЛИС",
            kindRaw = "Лабораторная работа",
            type = LessonType.LAB,
            place = "ф 331б",
            who = "Костина Мария Алексеевна",
            people = listOf(PersonRef("Костина Мария Алексеевна", teacherId = "35612")),
            note = null,
            floor = "3 этаж",
            mapPath = "/map?r=331%D0%B1",
            mapRoom = "331б",
        )
        val days = (0..6).map { offset ->
            ScheduleDay(
                date = monday.plusDays(offset.toLong()),
                lessons = if (offset == 0) listOf(lesson) else emptyList(),
            )
        }
        return Week(
            sourceKey = "group:fit/023",
            title = "гр. 023",
            isTeacher = false,
            monday = monday,
            today = monday.plusDays(2),
            parity = "нечётная",
            days = days,
            sourceUrl = "https://timetable.tusur.ru/faculties/fit/groups/023",
            staleAt = null,
            fetchedAt = Instant.parse("2026-11-25T10:00:00Z"),
        )
    }

    @Test
    fun `roundtrip keeps all seven days including empty ones`() {
        val original = week()
        val weekEntity = original.toWeekEntity()
        val events = original.toEventEntities()
        val people = original.toPersonEntities()

        val restored = weekEntity.toDomain(events, people)

        assertEquals(7, restored.days.size)
        assertEquals(1, restored.days.first().lessons.size)
        assertTrue(restored.days.drop(1).all { it.lessons.isEmpty() })
        assertEquals("нечётная", restored.parity)
        assertEquals(original.fetchedAt, restored.fetchedAt)
    }

    @Test
    fun `positions are unique across the week`() {
        val original = week()
        val events = original.toEventEntities()
        assertEquals(events.size, events.map { it.position }.distinct().size)
    }

    @Test
    fun `people and map room survive storage`() {
        val original = week()
        val restored = original.toWeekEntity().toDomain(
            original.toEventEntities(),
            original.toPersonEntities(),
        )
        val lesson = restored.days.first().lessons.single()
        assertEquals("331б", lesson.mapRoom)
        assertEquals("teacher:35612", lesson.people.single().sourceKey)
        assertEquals(LessonType.LAB, lesson.type)
    }

    @Test
    fun `cached week keeps empty day metadata`() {
        val original = week()
        val entity = original.toWeekEntity()
        assertEquals(
            "2026-11-23,2026-11-24,2026-11-25,2026-11-26,2026-11-27,2026-11-28,2026-11-29",
            entity.days,
        )
    }
}
