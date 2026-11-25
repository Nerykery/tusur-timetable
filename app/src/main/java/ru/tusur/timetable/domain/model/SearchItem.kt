package ru.tusur.timetable.domain.model

/** Результат поиска по /api/search. */
data class SearchItem(
    val source: SourceRef,
    val label: String,
    val hint: String,
    val faculty: String? = null,
    val group: String? = null,
    val teacherId: String? = null,
) {
    val isGroup: Boolean get() = source.isGroup
    val typeLabelRes: Int
        get() = if (isGroup) {
            ru.tusur.timetable.R.string.search_type_group
        } else {
            ru.tusur.timetable.R.string.search_type_teacher
        }
}
