package ru.tusur.timetable.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.BuildingMap
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.Week
import java.time.LocalDate

interface TimetableRepository {

    /** Кэш из Room: сначала показываем его, затем обновляем в фоне. */
    fun observeCached(ref: SourceRef, monday: LocalDate): Flow<Week?>

    suspend fun cachedWeek(ref: SourceRef, monday: LocalDate): Week?

    /**
     * Сеть + запись в кэш. Внутри — TTL 5 минут в памяти, ретраи с backoff
     * на сетевых ошибках и (опционально) переход на официальный API.
     */
    suspend fun refresh(ref: SourceRef, monday: LocalDate, force: Boolean = false): Result<Week>

    suspend fun search(query: String): Result<List<SearchItem>>

    suspend fun recentSearches(limit: Int = 5): List<SearchItem>

    /** Подпись преподавателя для официального API (по id из кэша поиска). */
    suspend fun teacherLabel(teacherId: String): String?
}

interface BuildingRepository {
    suspend fun map(): BuildingMap

    suspend fun load(): Result<BuildingLoad>
}
