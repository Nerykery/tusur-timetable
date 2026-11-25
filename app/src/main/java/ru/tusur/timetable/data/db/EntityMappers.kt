package ru.tusur.timetable.data.db

import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.Week
import java.time.Instant

/** Маппинг Room ↔ домен. Позиции перенумеровываются сквозь неделю: это ключ сущности. */
fun WeekEntity.toDomain(
        events: List<EventEntity>,
        people: List<PersonEntity>,
    ): Week {
        val peopleByPosition = people.groupBy { it.position }
        val eventsByDate = events.groupBy { it.date }
        val parsedMonday = java.time.LocalDate.parse(monday)
        val dayDates = days.split(',')
            .mapNotNull { runCatching { java.time.LocalDate.parse(it.trim()) }.getOrNull() }
            .ifEmpty {
                eventsByDate.keys.mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            }
            // Старая запись без списка дней и без событий всё равно должна давать неделю.
            .ifEmpty { (0..6).map { parsedMonday.plusDays(it.toLong()) } }
            .sorted()

        val scheduleDays = dayDates.map { parsedDate ->
            ScheduleDay(
                date = parsedDate,
                lessons = eventsByDate[parsedDate.toString()]
                    .orEmpty()
                    .sortedBy { it.position }
                    .mapNotNull { event ->
                        Lesson(
                            position = event.position,
                            date = parsedDate,
                            startMinutes = event.startMinutes,
                            endMinutes = event.endMinutes,
                            pair = event.pair,
                            title = event.title,
                            kindRaw = event.kind,
                            type = runCatching { LessonType.valueOf(event.type) }
                                .getOrDefault(LessonType.OTHER),
                            place = event.place,
                            who = event.who,
                            people = peopleByPosition[event.position]
                                .orEmpty()
                                .sortedBy { it.personIndex }
                                .map {
                                    PersonRef(
                                        label = it.label,
                                        teacherId = it.teacherId,
                                        groupKey = it.groupKey,
                                    )
                                },
                            note = event.note,
                            floor = event.floor,
                            mapPath = event.mapPath,
                            mapRoom = event.mapRoom,
                        )
                    },
            )
        }

        return Week(
            sourceKey = sourceKey,
            title = title,
            isTeacher = isTeacher,
            monday = java.time.LocalDate.parse(monday),
            today = today?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            parity = parity,
            days = scheduleDays,
            sourceUrl = sourceUrl,
            staleAt = staleAt,
            fetchedAt = Instant.ofEpochMilli(fetchedAt),
        )
    }

    fun Week.toWeekEntity(): WeekEntity = WeekEntity(
        sourceKey = sourceKey,
        monday = monday.toString(),
        title = title,
        isTeacher = isTeacher,
        today = today?.toString(),
        parity = parity,
        sourceUrl = sourceUrl,
        staleAt = staleAt,
        fetchedAt = fetchedAt.toEpochMilli(),
        days = days.map { it.date.toString() }.joinToString(","),
    )

    fun Week.toEventEntities(): List<EventEntity> {
        var globalPosition = 0
        return days
            .sortedBy { it.date }
            .flatMap { day ->
                day.lessons.sortedBy { it.startMinutes }.map { lesson ->
                    EventEntity(
                        sourceKey = sourceKey,
                        monday = monday.toString(),
                        position = globalPosition++,
                        date = day.date.toString(),
                        startMinutes = lesson.startMinutes,
                        endMinutes = lesson.endMinutes,
                        pair = lesson.pair,
                        title = lesson.title,
                        kind = lesson.kindRaw,
                        type = lesson.type.name,
                        place = lesson.place,
                        who = lesson.who,
                        note = lesson.note,
                        floor = lesson.floor,
                        mapPath = lesson.mapPath,
                        mapRoom = lesson.mapRoom,
                    )
                }
            }
    }

    fun Week.toPersonEntities(): List<PersonEntity> {
        var globalPosition = 0
        val result = mutableListOf<PersonEntity>()
        days.sortedBy { it.date }.forEach { day ->
            day.lessons.sortedBy { it.startMinutes }.forEach { lesson ->
                lesson.people.forEachIndexed { personIndex, person ->
                    result += PersonEntity(
                        sourceKey = sourceKey,
                        monday = monday.toString(),
                        position = globalPosition,
                        personIndex = personIndex,
                        label = person.label,
                        teacherId = person.teacherId,
                        groupKey = person.groupKey,
                    )
                }
                globalPosition++
            }
        }
        return result
}
