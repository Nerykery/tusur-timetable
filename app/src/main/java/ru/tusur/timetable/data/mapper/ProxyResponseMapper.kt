package ru.tusur.timetable.data.mapper

import ru.tusur.timetable.core.room.RoomRefs
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.network.dto.BusyDto
import ru.tusur.timetable.data.network.dto.EventDto
import ru.tusur.timetable.data.network.dto.LoadDto
import ru.tusur.timetable.data.network.dto.PersonDto
import ru.tusur.timetable.data.network.dto.SearchDto
import ru.tusur.timetable.data.network.dto.SearchItemDto
import ru.tusur.timetable.data.network.dto.WeekDto
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.BusyLesson
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.LoadedPair
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.RoomBusy
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.model.parityLabelFrom
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Преобразование ответов прокси-API в доменные модели. */
@Singleton
class ProxyResponseMapper @Inject constructor() {

    fun mapWeek(dto: WeekDto, requested: SourceRef, fetchedAt: Instant): Week {
        val monday = TomskTime.parseDate(dto.monday)
            ?: error("Некорректная дата недели: ${dto.monday}")
        val days = dto.days
            .mapNotNull { day ->
                val date = TomskTime.parseDate(day.date) ?: return@mapNotNull null
                ScheduleDay(
                    date = date,
                    lessons = day.events
                        .mapIndexedNotNull { index, event -> event.toLesson(date, index) }
                        .sortedBy { it.startMinutes },
                )
            }
            .sortedBy { it.date }

        return Week(
            sourceKey = requested.key,
            title = dto.title.ifBlank { requested.fallbackLabel },
            isTeacher = dto.teacher,
            monday = monday,
            today = TomskTime.parseDate(dto.today),
            parity = parityLabelFrom(dto.parity),
            days = days,
            sourceUrl = dto.source,
            staleAt = dto.stale?.takeIf { it.isNotBlank() },
            fetchedAt = fetchedAt,
        )
    }

    fun mapSearch(dto: SearchDto): List<SearchItem> = dto.found.mapNotNull { it.toSearchItem() }

    fun mapLoad(dto: LoadDto): BuildingLoad {
        val pairs = dto.pairs.mapIndexed { index, pair ->
            val start = TomskTime.parseTimeToMinutes(pair.getOrNull(0)) ?: 0
            val end = TomskTime.parseTimeToMinutes(pair.getOrNull(1)) ?: start
            LoadedPair(index = index, startMinutes = start, endMinutes = end)
        }
        val rooms = dto.rooms.mapValues { (_, room) ->
            RoomBusy(
                href = room.href,
                cells = room.busy.mapNotNull { (cell, lessons) ->
                    val index = cell.toIntOrNull() ?: return@mapNotNull null
                    index to lessons.map { it.toBusyLesson() }
                }.toMap(),
            )
        }
        return BuildingLoad(
            monday = TomskTime.parseDate(dto.monday) ?: TomskTime.today(),
            parity = parityLabelFrom(dto.parity),
            pairs = pairs,
            rooms = rooms,
        )
    }

    private fun BusyDto.toBusyLesson(): BusyLesson = BusyLesson(
        title = t,
        group = g,
        who = w,
        kind = k,
    )

    private fun SearchItemDto.toSearchItem(): SearchItem? = when (type) {
        "group" -> {
            val group = group?.takeIf { it.isNotBlank() } ?: return null
            val faculty = faculty?.takeIf { it.isNotBlank() }
            val id = if (faculty != null) "$faculty/$group" else group
            SearchItem(
                source = SourceRef.group(id, label),
                label = label.ifBlank { "гр. $group" },
                hint = hint,
                faculty = faculty,
                group = group,
            )
        }

        "teacher" -> {
            val teacherId = id?.takeIf { it.isNotBlank() } ?: return null
            SearchItem(
                source = SourceRef.teacher(teacherId, label),
                label = label,
                hint = hint,
                teacherId = teacherId,
            )
        }

        else -> null
    }
}

internal fun EventDto.toLesson(date: java.time.LocalDate, index: Int): Lesson? {
    val start = TomskTime.parseTimeToMinutes(start) ?: return null
    val end = TomskTime.parseTimeToMinutes(end) ?: start
    return Lesson(
        position = index,
        date = date,
        startMinutes = start,
        endMinutes = end,
        pair = pair,
        title = title.ifBlank { "Занятие" },
        kindRaw = kind,
        type = LessonType.fromKind(kind),
        place = place?.takeIf { it.isNotBlank() },
        who = who?.takeIf { it.isNotBlank() },
        people = people.mapNotNull { it.toPersonRef() },
        note = note?.takeIf { it.isNotBlank() },
        floor = floor?.takeIf { it.isNotBlank() },
        mapPath = map?.takeIf { it.isNotBlank() },
        mapRoom = RoomRefs.roomFromMapPath(map),
    )
}

private fun PersonDto.toPersonRef(): PersonRef? {
    val teacherId = teacher?.takeIf { it.isNotBlank() }
    val groupKey = group?.takeIf { it.isNotBlank() }
    if (teacherId == null && groupKey == null) return null
    return PersonRef(label = label, teacherId = teacherId, groupKey = groupKey)
}
