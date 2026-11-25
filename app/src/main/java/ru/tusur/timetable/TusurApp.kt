package ru.tusur.timetable

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ru.tusur.timetable.notifications.NotificationChannels
import ru.tusur.timetable.work.WorkScheduler
import javax.inject.Inject

@HiltAndroidApp
class TusurApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        WorkScheduler.schedulePeriodic(this)
    }
}
