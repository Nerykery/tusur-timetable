package ru.tusur.timetable.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.repository.TimetableRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
    private val repository: TimetableRepository,
    private val texts: ReminderNotificationTexts,
    private val clock: Clock,
) {

    suspend fun rescheduleAll() {
        cancelScheduled()

        val settings = prefs.settings.first()
        if (!settings.notificationsEnabled) return

        val selection = prefs.selectionOnce() ?: return
        val ref = SourceRef.parse(selection.key)?.withLabel(selection.label) ?: return

        val today = TomskTime.today(clock)
        val mondays = listOf(
            TomskTime.mondayOf(today),
            TomskTime.mondayOf(today.plusDays(1)),
        ).distinct()

        val weeks = mondays.mapNotNull { repository.cachedWeek(ref, it) }
        if (weeks.isEmpty()) return

        val planned = ReminderPlanner.plan(weeks, settings, TomskTime.now(clock))
        val codes = mutableSetOf<String>()
        planned.forEach { reminder ->
            schedule(reminder)
            codes += reminder.requestCode.toString()
        }
        prefs.setScheduledAlarmCodes(codes)
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    private suspend fun cancelScheduled() {
        val codes = prefs.scheduledAlarmCodes()
        if (codes.isEmpty()) return
        val manager = context.getSystemService(AlarmManager::class.java)
        codes.forEach { raw ->
            val code = raw.toIntOrNull() ?: return@forEach
            val pending = pendingIntent(code, PendingIntent.FLAG_NO_CREATE)
            if (manager != null && pending != null) {
                manager.cancel(pending)
                pending.cancel()
            }
        }
        prefs.setScheduledAlarmCodes(emptySet())
    }

    private fun schedule(reminder: ReminderPlanner.PlannedReminder) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return

        val content = reminder.content
        val title = texts.title(content)
        val text = texts.text(content)

        val intent = baseIntent().apply {
            putExtra(ReminderReceiver.EXTRA_ID, reminder.notificationId)
            putExtra(ReminderReceiver.EXTRA_CHANNEL, reminder.channel)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
            putExtra(ReminderReceiver.EXTRA_DEEP_LINK, reminder.deepLink)
            putExtra(ReminderReceiver.EXTRA_EVENT_KEY, reminder.eventKey)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            reminder.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pending,
            )
        } else {
            // Без разрешения на точные будильники система может сдвинуть время — UI честно предупреждает.
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pending,
            )
        }
    }

    private fun pendingIntent(requestCode: Int, extraFlags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            baseIntent(),
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun baseIntent(): Intent = Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_REMIND
    }
}
