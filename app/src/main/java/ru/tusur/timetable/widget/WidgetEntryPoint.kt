package ru.tusur.timetable.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ru.tusur.timetable.data.db.WidgetDao

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun snapshotProvider(): WidgetSnapshotProvider

    fun widgetDao(): WidgetDao
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )

enum class WidgetDisplayMode {
    TODAY,
    NEXT,
    ;

    companion object {
        fun from(raw: String?): WidgetDisplayMode =
            entries.firstOrNull { it.name == raw } ?: NEXT
    }
}

data class WidgetDay(
    val date: java.time.LocalDate,
    val lessonCount: Int,
)

data class WidgetSnapshot(
    val hasSource: Boolean = false,
    val hasCache: Boolean = false,
    val sourceTitle: String = "",
    val sourceKey: String? = null,
    val today: java.time.LocalDate = java.time.LocalDate.now(),
    val nowMinutes: Int = 0,
    val lessons: List<ru.tusur.timetable.domain.model.Lesson> = emptyList(),
    val status: ru.tusur.timetable.domain.model.DayStatus =
        ru.tusur.timetable.domain.model.DayStatus(null, null, null, null, false, true),
    val week: List<WidgetDay> = emptyList(),
    val staleAt: String? = null,
) {
    val isEmpty: Boolean get() = !hasSource || !hasCache
}
