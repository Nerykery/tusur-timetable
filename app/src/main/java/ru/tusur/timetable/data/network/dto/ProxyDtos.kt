package ru.tusur.timetable.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Контракт API расписания: /api/week, /api/search, /api/load.
 * Поля повторяют ответы один в один; отсутствующие значения — null.
 */
@Serializable
data class WeekDto(
    val title: String = "",
    val teacher: Boolean = false,
    val monday: String = "",
    val today: String? = null,
    val parity: String? = null,
    val days: List<DayDto> = emptyList(),
    val source: String? = null,
    val stale: String? = null,
)

@Serializable
data class DayDto(
    val date: String = "",
    val events: List<EventDto> = emptyList(),
)

@Serializable
data class EventDto(
    val start: String = "",
    val end: String = "",
    val pair: Int? = null,
    val title: String = "",
    val kind: String? = null,
    val place: String? = null,
    val who: String? = null,
    val people: List<PersonDto> = emptyList(),
    val note: String? = null,
    val floor: String? = null,
    val map: String? = null,
)

@Serializable
data class PersonDto(
    val label: String = "",
    val teacher: String? = null,
    val group: String? = null,
)

@Serializable
data class SearchDto(
    val found: List<SearchItemDto> = emptyList(),
)

@Serializable
data class SearchItemDto(
    val type: String = "",
    val faculty: String? = null,
    val group: String? = null,
    val label: String = "",
    val hint: String = "",
    val id: String? = null,
    val only: Boolean = false,
)

@Serializable
data class LoadDto(
    val monday: String? = null,
    val parity: String? = null,
    val pairs: List<List<String>> = emptyList(),
    val rooms: Map<String, RoomDto> = emptyMap(),
)

@Serializable
data class RoomDto(
    val href: String? = null,
    val busy: Map<String, List<BusyDto>> = emptyMap(),
)

@Serializable
data class BusyDto(
    val t: String = "",
    val g: String = "",
    val w: String = "",
    val k: String = "",
)
