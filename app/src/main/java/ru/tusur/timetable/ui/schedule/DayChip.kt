package ru.tusur.timetable.ui.schedule

import java.time.LocalDate

/** День в переключателе: дата и признак «есть пары» для точки-индикатора. */
data class DayChip(
    val date: LocalDate,
    val hasLessons: Boolean,
)
