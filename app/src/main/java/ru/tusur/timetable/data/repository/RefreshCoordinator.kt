package ru.tusur.timetable.data.repository

import ru.tusur.timetable.notifications.ReminderScheduler
import ru.tusur.timetable.widget.WidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Побочные эффекты после успешного обновления расписания: виджеты и будильники.
 * Точка входа одна для экранов и для WorkManager.
 */
@Singleton
class RefreshCoordinator @Inject constructor(
    private val widgets: WidgetUpdater,
    private val reminders: ReminderScheduler,
) {

    suspend fun onScheduleUpdated() {
        widgets.updateAll()
        reminders.rescheduleAll()
    }
}
