package ru.tusur.timetable.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weeks", primaryKeys = ["sourceKey", "monday"])
data class WeekEntity(
    val sourceKey: String,
    val monday: String,
    val title: String,
    val isTeacher: Boolean,
    val today: String?,
    val parity: String?,
    val sourceUrl: String?,
    val staleAt: String?,
    val fetchedAt: Long,
    /** Даты всех дней недели через запятую: пустые дни тоже нужно помнить. */
    val days: String,
)

@Entity(
    tableName = "events",
    primaryKeys = ["sourceKey", "monday", "position"],
    indices = [Index(value = ["sourceKey", "monday"])],
)
data class EventEntity(
    val sourceKey: String,
    val monday: String,
    val position: Int,
    val date: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val pair: Int?,
    val title: String,
    val kind: String?,
    val type: String,
    val place: String?,
    val who: String?,
    val note: String?,
    val floor: String?,
    val mapPath: String?,
    val mapRoom: String?,
)

@Entity(
    tableName = "people",
    primaryKeys = ["sourceKey", "monday", "position", "personIndex"],
    indices = [Index(value = ["sourceKey", "monday"])],
)
data class PersonEntity(
    val sourceKey: String,
    val monday: String,
    val position: Int,
    val personIndex: Int,
    val label: String,
    val teacherId: String?,
    val groupKey: String?,
)

/** Справочник последних поисков — нужен для офлайна и для подписи преподавателя. */
@Entity(tableName = "search_cache", primaryKeys = ["type", "id"])
data class SearchCacheEntity(
    val type: String,
    val id: String,
    val label: String,
    val hint: String,
    val faculty: String?,
    val group: String?,
    val teacherId: String?,
    val only: Boolean,
    val lastSeenAt: Long,
)

/** Настройки каждого экземпляра настраиваемого виджета. */
@Entity(tableName = "widget_prefs")
data class WidgetPrefsEntity(
    @PrimaryKey val widgetId: String,
    val sourceKey: String?,
    val label: String?,
    val mode: String,
    val updatedAt: Long,
)
