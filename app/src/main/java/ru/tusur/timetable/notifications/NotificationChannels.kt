package ru.tusur.timetable.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import ru.tusur.timetable.R

object NotificationChannels {

    const val REMINDERS = "reminders"
    const val DIGEST = "digest"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
        }

        val digest = NotificationChannel(
            DIGEST,
            context.getString(R.string.channel_digest),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_digest_description)
        }

        manager.createNotificationChannel(reminders)
        manager.createNotificationChannel(digest)
    }
}
