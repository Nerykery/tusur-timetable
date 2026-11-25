package ru.tusur.timetable.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val PERIODIC_REFRESH = "tusur_periodic_refresh"
    private const val IMMEDIATE_REFRESH = "tusur_immediate_refresh"
    private const val MAINTENANCE = "tusur_maintenance"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Страховка: раз в 30 минут. Точные обновления — по событиям приложения. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Немедленное обновление: старт приложения, выбор группы, добавление виджета. */
    fun requestImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_REFRESH,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Без сети: пересобрать будильники и виджеты из кэша. */
    fun requestMaintenance(context: Context) {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MAINTENANCE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
