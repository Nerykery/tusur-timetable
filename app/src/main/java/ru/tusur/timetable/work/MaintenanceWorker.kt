package ru.tusur.timetable.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.tusur.timetable.notifications.ReminderScheduler
import ru.tusur.timetable.widget.WidgetUpdater

@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val widgets: WidgetUpdater,
    private val reminders: ReminderScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        widgets.updateAll()
        reminders.rescheduleAll()
        return Result.success()
    }
}
