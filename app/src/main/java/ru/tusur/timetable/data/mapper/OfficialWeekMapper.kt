package ru.tusur.timetable.data.mapper

import ru.tusur.timetable.core.room.RoomRefs
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.network.dto.OfficialDayDto
import ru.tusur.timetable.data.network.dto.OfficialLessonDto
import ru.tusur.timetable.data.network.dto.OfficialLecturerDto
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.model.parityLabelFrom
import java.time.Instant
import java.time.LocalDate

/**
 * Официальный API отдаёт плоский список дней с уроками, группы — строкой через
 * запятую, а вместо id группы сайта — groupNumber (746-1, 716топ-1).
 */
object OfficialWeekMapper {

    fun map(
        days: List<OfficialDayDto>,
        ref: SourceRef,
        monday: LocalDate,
        parity: String?,
        fetchedAt: Instant,
        today: LocalDate,
    ): Week {
        val mappedDays = days
            .mapNotNull { day ->
                val date = TomskTime.parseDate(day.date) ?: return@mapNotNull null
                ScheduleDay(
                    date = date,
                    lessons = day.lessons
                        .mapIndexedNotNull { index, lesson -> lesson.toLesson(date, index, ref) }
                        .sortedBy { it.startMinutes },
                )
            }
            .sortedBy { it.date }
            .ifEmpty {
                // Официальный API может не вернуть дни без занятий — достраиваем неделю.
                (0..6).map { offset ->
                    val date = monday.plusDays(offset.toLong())
                    ScheduleDay(date, emptyList())
                }
            }

        return Week(
            sourceKey = ref.key,
            title = ref.displayLabel?.takeIf { it.isNotBlank() }
                ?: if (ref.isGroup) {
                    "гр. ${ref.officialGroupId ?: ref.id}"
                } else {
                    "преп. ${ref.officialTeacherName ?: "—"}"
                },
            isTeacher = ref.isTeacher,
            monday = monday,
            today = if (TomskTime.mondayOf(today) == monday) today else null,
            parity = parityLabelFrom(parity),
            days = mappedDays,
            sourceUrl = "https://timetable.tusur.ru/",
            staleAt = null,
            fetchedAt = fetchedAt,
        )
    }

    private fun OfficialLessonDto.toLesson(
        date: LocalDate,
        index: Int,
        ref: SourceRef,
    ): Lesson? {
        val start = TomskTime.parseTimeToMinutes(startTime) ?: return null
        val end = TomskTime.parseTimeToMinutes(endTime) ?: start
        val roomInFeet = if (classroom.contains("ф", ignoreCase = true)) {
            RoomRefs.roomFromPlace(classroom)
        } else {
            null
        }
        val people = if (ref.isGroup) {
            lecturers.mapNotNull { it.toTeacherRef() }
        } else {
            groupRefs()
        }
        return Lesson(
            position = index,
            date = date,
            startMinutes = start,
            endMinutes = end,
            pair = number,
            title = name.ifBlank { abbr.orEmpty().ifBlank { "Занятие" } },
            kindRaw = kind,
            type = LessonType.fromKind(kind),
            place = classroom.takeIf { it.isNotBlank() },
            who = if (ref.isGroup) {
                lecturers.joinToString(", ") { it.shortName() }.takeIf { it.isNotBlank() }
            } else {
                groups?.takeIf { it.isNotBlank() }
            },
            people = people,
            note = null,
            floor = RoomRefs.floorFromRoom(roomInFeet)?.let { "$it этаж" },
            mapPath = roomInFeet?.let(RoomRefs::mapPathFor),
            mapRoom = roomInFeet,
        )
    }

    private fun OfficialLessonDto.groupRefs(): List<PersonRef> =
        groups.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { PersonRef(label = it, groupKey = it) }

    private fun OfficialLecturerDto.toTeacherRef(): PersonRef? {
        val id = id?.toString() ?: return null
        val label = fullName.takeIf { it.isNotBlank() } ?: return null
        return PersonRef(label = label, teacherId = id)
    }

    private fun OfficialLecturerDto.shortName(): String {
        val last = lastName.trim()
        val initial = firstName.trim().firstOrNull()?.uppercaseChar()
        val middle = middleName.trim().firstOrNull()?.uppercaseChar()
        return when {
            last.isEmpty() -> fullName
            initial == null -> last
            middle == null -> "$last $initial."
            else -> "$last $initial.$middle."
        }
    }
}
