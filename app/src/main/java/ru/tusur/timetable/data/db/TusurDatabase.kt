package ru.tusur.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Dao
interface WidgetDao {

    @Query("SELECT * FROM widget_prefs WHERE widgetId = :widgetId LIMIT 1")
    suspend fun get(widgetId: String): WidgetPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WidgetPrefsEntity)

    @Query("DELETE FROM widget_prefs WHERE widgetId = :widgetId")
    suspend fun delete(widgetId: String)

    @Query("SELECT * FROM widget_prefs")
    suspend fun all(): List<WidgetPrefsEntity>
}

@androidx.room.Database(
    entities = [
        WeekEntity::class,
        EventEntity::class,
        PersonEntity::class,
        SearchCacheEntity::class,
        WidgetPrefsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TusurDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao

    abstract fun widgetDao(): WidgetDao
}
