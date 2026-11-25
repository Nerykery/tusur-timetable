package ru.tusur.timetable.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tusur.timetable.widget.nextclass.NextClassWidget
import ru.tusur.timetable.widget.quick.QuickPickWidget
import ru.tusur.timetable.widget.today.TodayWidget
import ru.tusur.timetable.widget.week.WeekWidget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun updateAll() = withContext(Dispatchers.Default) {
        val widgets = listOf(
            NextClassWidget(),
            TodayWidget(),
            WeekWidget(),
            QuickPickWidget(),
        )
        widgets.forEach { widget ->
            runCatching { widget.updateAll(context) }
        }
    }
}
