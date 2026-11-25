package ru.tusur.timetable.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Официальный публичный API ТУСУР: /api/v1/timetable/lessons, /lessons/lecturer, /weeks.
 * Авторизация для расписания не нужна. id группы — groupNumber из профиля
 * (формат NNN-N, например 746-1; возможен вариант 716топ-1).
 */
@Serializable
data class OfficialLessonsResponse(
    val data: List<OfficialDayDto> = emptyList(),
)

@Serializable
data class OfficialDayDto(
    val date: String = "",
    val lessons: List<OfficialLessonDto> = emptyList(),
)

@Serializable
data class OfficialLessonDto(
    val day: Int? = null,
    val number: Int? = null,
    val name: String = "",
    val abbr: String? = null,
    val kind: String? = null,
    val startTime: String = "",
    val endTime: String = "",
    val classroom: String = "",
    val groups: String? = null,
    val lecturers: List<OfficialLecturerDto> = emptyList(),
)

@Serializable
data class OfficialLecturerDto(
    val id: Int? = null,
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
) {
    val fullName: String
        get() = listOf(lastName, firstName, middleName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

@Serializable
data class OfficialWeeksResponse(
    val data: List<OfficialWeekInfoDto> = emptyList(),
)

@Serializable
data class OfficialWeekInfoDto(
    val parity: String = "",
    val startsAt: String = "",
    val timetable: String? = null,
    val weekId: Int? = null,
    val weekNumber: Int? = null,
)
