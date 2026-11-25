package ru.tusur.timetable.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.TimetableException
import ru.tusur.timetable.domain.repository.TimetableRepository
import ru.tusur.timetable.notifications.ReminderScheduler
import ru.tusur.timetable.widget.WidgetUpdater
import java.time.Clock

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TimetableRepository,
    private val prefs: UserPreferences,
    private val widgets: WidgetUpdater,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val selection = prefs.selectionOnce() ?: return Result.success()
        val ref = SourceRef.parse(selection.key)?.withLabel(selection.label)
            ?: return Result.success()

        val today = TomskTime.today(clock)
        val monday = TomskTime.mondayOf(today)

        val primary = repository.refresh(ref, monday, force = true)

        // В воскресенье заранее подтягиваем следующую неделю — для вечерней сводки.
        if (today.dayOfWeek.value == 7) {
            runCatching { repository.refresh(ref, monday.plusWeeks(1), force = false) }
        }

        widgets.updateAll()
        reminders.rescheduleAll()

        val error = (primary.exceptionOrNull() as? TimetableException)?.error
        return if (error?.isRetryable == true) Result.retry() else Result.success()
    }
}
