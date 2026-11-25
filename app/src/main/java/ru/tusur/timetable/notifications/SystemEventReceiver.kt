package ru.tusur.timetable.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.tusur.timetable.work.WorkScheduler

/**
 * Перезагрузка, смена времени/даты/пояса и обновление приложения: пересобрать
 * будильники и обновить виджеты (WorkManager переживает reboot, будильники — нет).
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return
        WorkScheduler.requestMaintenance(context)
        WorkScheduler.requestImmediate(context)
    }
}
