package ru.tusur.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TimetableDao {

    // ---------- расписание ----------

    @Query("SELECT * FROM weeks WHERE sourceKey = :sourceKey AND monday = :monday LIMIT 1")
    abstract fun observeWeek(sourceKey: String, monday: String): Flow<WeekEntity?>

    @Query("SELECT * FROM events WHERE sourceKey = :sourceKey AND monday = :monday ORDER BY date, position")
    abstract fun observeEvents(sourceKey: String, monday: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM people WHERE sourceKey = :sourceKey AND monday = :monday ORDER BY position, personIndex")
    abstract fun observePeople(sourceKey: String, monday: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM weeks WHERE sourceKey = :sourceKey AND monday = :monday LIMIT 1")
    abstract suspend fun weekOnce(sourceKey: String, monday: String): WeekEntity?

    @Query("SELECT * FROM events WHERE sourceKey = :sourceKey AND monday = :monday ORDER BY date, position")
    abstract suspend fun eventsOnce(sourceKey: String, monday: String): List<EventEntity>

    @Query("SELECT * FROM people WHERE sourceKey = :sourceKey AND monday = :monday ORDER BY position, personIndex")
    abstract suspend fun peopleOnce(sourceKey: String, monday: String): List<PersonEntity>

    @Query("SELECT monday FROM weeks WHERE sourceKey = :sourceKey ORDER BY monday DESC LIMIT :limit")
    abstract suspend fun cachedMondays(sourceKey: String, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertWeek(week: WeekEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertEvents(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertPeople(people: List<PersonEntity>)

    @Query("DELETE FROM events WHERE sourceKey = :sourceKey AND monday = :monday")
    protected abstract suspend fun deleteEvents(sourceKey: String, monday: String)

    @Query("DELETE FROM people WHERE sourceKey = :sourceKey AND monday = :monday")
    protected abstract suspend fun deletePeople(sourceKey: String, monday: String)

    @Query("DELETE FROM weeks WHERE sourceKey = :sourceKey AND monday = :monday")
    protected abstract suspend fun deleteWeek(sourceKey: String, monday: String)

    @Transaction
    open suspend fun replaceWeek(
        week: WeekEntity,
        events: List<EventEntity>,
        people: List<PersonEntity>,
    ) {
        deletePeople(week.sourceKey, week.monday)
        deleteEvents(week.sourceKey, week.monday)
        deleteWeek(week.sourceKey, week.monday)
        upsertWeek(week)
        if (events.isNotEmpty()) upsertEvents(events)
        if (people.isNotEmpty()) upsertPeople(people)
    }

    // ---------- справочник поиска ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSearch(items: List<SearchCacheEntity>)

    @Query("SELECT * FROM search_cache ORDER BY lastSeenAt DESC LIMIT :limit")
    abstract suspend fun recentSearches(limit: Int): List<SearchCacheEntity>

    @Query(
        """
        SELECT * FROM search_cache
        WHERE (type = 'group' AND (id LIKE :like OR label LIKE :like))
           OR (type = 'teacher' AND label LIKE :like)
        ORDER BY lastSeenAt DESC
        LIMIT 8
        """,
    )
    abstract suspend fun offlineSearch(like: String): List<SearchCacheEntity>

    @Query("SELECT label FROM search_cache WHERE type = 'teacher' AND teacherId = :teacherId LIMIT 1")
    abstract suspend fun teacherLabel(teacherId: String): String?

    @Query("DELETE FROM search_cache WHERE lastSeenAt < :threshold")
    abstract suspend fun pruneSearchCache(threshold: Long)
}
