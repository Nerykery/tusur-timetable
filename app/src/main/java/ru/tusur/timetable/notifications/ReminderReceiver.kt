package ru.tusur.timetable.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.tusur.timetable.data.prefs.UserPreferences
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: UserPreferences

    @Inject
    lateinit var notifications: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val eventKey = intent.getStringExtra(EXTRA_EVENT_KEY)
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationChannels.REMINDERS
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val deepLink = intent.getStringExtra(EXTRA_DEEP_LINK) ?: "timetable://day"

        if (id <= 0 || eventKey.isNullOrBlank() || title.isBlank()) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (prefs.markReminderSent(eventKey)) {
                    notifications.notify(
                        id = id,
                        channel = channel,
                        title = title,
                        text = text,
                        deepLink = deepLink,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "ru.tusur.timetable.action.REMIND"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_DEEP_LINK = "extra_deep_link"
        const val EXTRA_EVENT_KEY = "extra_event_key"
    }
}
