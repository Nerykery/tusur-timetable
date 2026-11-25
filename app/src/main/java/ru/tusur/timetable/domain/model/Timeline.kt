package ru.tusur.timetable.domain.model

/** Перерыв — до 30 минут между парами; больше — «окно». */
const val BREAK_MAX_MINUTES: Int = 30

enum class GapKind { BREAK, WINDOW }

sealed interface TimelineItem {
    data class LessonItem(val lesson: Lesson) : TimelineItem

    data class GapItem(
        val startMinutes: Int,
        val endMinutes: Int,
        val kind: GapKind,
        val previousTitle: String?,
    ) : TimelineItem {
        val duration: Int get() = endMinutes - startMinutes
    }
}

/**
 * Лента дня: пары и промежутки между ними (перерывы/окна) в порядке времени.
 */
fun buildTimeline(lessons: List<Lesson>): List<TimelineItem> {
    val sorted = lessons.sortedBy { it.startMinutes }
    val items = mutableListOf<TimelineItem>()
    sorted.forEachIndexed { index, lesson ->
        items += TimelineItem.LessonItem(lesson)
        val next = sorted.getOrNull(index + 1) ?: return@forEachIndexed
        val gap = next.startMinutes - lesson.endMinutes
        if (gap > 0) {
            items += TimelineItem.GapItem(
                startMinutes = lesson.endMinutes,
                endMinutes = next.startMinutes,
                kind = if (gap <= BREAK_MAX_MINUTES) GapKind.BREAK else GapKind.WINDOW,
                previousTitle = lesson.title,
            )
        }
    }
    return items
}
