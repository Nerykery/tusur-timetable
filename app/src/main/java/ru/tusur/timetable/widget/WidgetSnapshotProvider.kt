package ru.tusur.timetable.widget

import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.db.WidgetDao
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.model.computeDayStatus
import ru.tusur.timetable.domain.repository.TimetableRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSnapshotProvider @Inject constructor(
    private val repository: TimetableRepository,
    private val prefs: UserPreferences,
    private val widgetDao: WidgetDao,
    private val clock: Clock,
) {

    /** Виджеты «Следующая пара», «Сегодня», «Неделя» — по общему выбору расписания. */
    suspend fun forApp(): WidgetSnapshot {
        val selection = prefs.selectionOnce()
        val ref = SourceRef.parse(selection?.key)?.withLabel(selection?.label)
        return build(ref, selection?.label)
    }

    /** Настраиваемый виджет: расписание и режим хранятся для каждого экземпляра. */
    suspend fun forWidget(widgetId: String): Pair<WidgetSnapshot, WidgetDisplayMode> {
        val config = widgetDao.get(widgetId)
        val mode = WidgetDisplayMode.from(config?.mode)
        val ref = SourceRef.parse(config?.sourceKey)?.withLabel(config?.label)
        if (ref == null) return WidgetSnapshot() to mode
        return build(ref, config?.label) to mode
    }

    private suspend fun build(ref: SourceRef?, label: String?): WidgetSnapshot {
        val now = TomskTime.now(clock)
        val today = now.toLocalDate()
        val nowMinutes = now.hour * 60 + now.minute

        if (ref == null) {
            return WidgetSnapshot(today = today, nowMinutes = nowMinutes)
        }

        val monday = TomskTime.mondayOf(today)
        val week: Week? = repository.cachedWeek(ref, monday)
        val title = week?.title?.takeIf { it.isNotBlank() }
            ?: label?.takeIf { it.isNotBlank() }
            ?: ref.fallbackLabel

        if (week == null) {
            return WidgetSnapshot(
                hasSource = true,
                hasCache = false,
                sourceTitle = title,
                sourceKey = ref.key,
                today = today,
                nowMinutes = nowMinutes,
            )
        }

        val lessons = week.day(today)?.lessons.orEmpty()
        return WidgetSnapshot(
            hasSource = true,
            hasCache = true,
            sourceTitle = title,
            sourceKey = ref.key,
            today = today,
            nowMinutes = nowMinutes,
            lessons = lessons,
            status = computeDayStatus(lessons, nowMinutes),
            week = week.days.map { WidgetDay(it.date, it.lessonCount) },
            staleAt = week.staleAt,
        )
    }

    /** Дата ближайшего понедельника нужна только для запроса; оставляем помощник для тестов. */
    fun mondayFor(date: LocalDate): LocalDate = TomskTime.mondayOf(date)
}
