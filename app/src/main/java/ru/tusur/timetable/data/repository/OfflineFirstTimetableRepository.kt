package ru.tusur.timetable.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.db.toDomain
import ru.tusur.timetable.data.db.toEventEntities
import ru.tusur.timetable.data.db.toPersonEntities
import ru.tusur.timetable.data.db.toWeekEntity
import ru.tusur.timetable.data.db.SearchCacheEntity
import ru.tusur.timetable.data.db.TimetableDao
import ru.tusur.timetable.data.network.ApiErrorMapper
import ru.tusur.timetable.data.network.NetworkRetry
import ru.tusur.timetable.data.network.OfficialRemoteSource
import ru.tusur.timetable.data.network.ProxyRemoteSource
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.DataSourceMode
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.domain.model.TimetableException
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.repository.TimetableRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstTimetableRepository @Inject constructor(
    private val dao: TimetableDao,
    private val proxy: ProxyRemoteSource,
    private val official: OfficialRemoteSource,
    private val prefs: UserPreferences,
    private val errorMapper: ApiErrorMapper,
    private val clock: Clock,
) : TimetableRepository {

    private val memory = ConcurrentHashMap<String, MemoryEntry>()
    private val mutex = Mutex()
    private var lastPrune: Instant = Instant.EPOCH

    private data class MemoryEntry(val fetchedAt: Instant, val week: Week)

    override fun observeCached(ref: SourceRef, monday: LocalDate): Flow<Week?> {
        val key = ref.key
        val date = monday.toString()
        return combine(
            dao.observeWeek(key, date),
            dao.observeEvents(key, date),
            dao.observePeople(key, date),
        ) { week, events, people ->
            week?.toDomain(events, people)
        }.distinctUntilChanged()
    }

    override suspend fun cachedWeek(ref: SourceRef, monday: LocalDate): Week? =
        withContext(Dispatchers.IO) {
            val key = ref.key
            val date = monday.toString()
            val week = dao.weekOnce(key, date) ?: return@withContext null
            week.toDomain(dao.eventsOnce(key, date), dao.peopleOnce(key, date))
        }

    override suspend fun refresh(
        ref: SourceRef,
        monday: LocalDate,
        force: Boolean,
    ): Result<Week> = withContext(Dispatchers.IO) {
        val today = TomskTime.today(clock)
        if (!TomskTime.isWithinWindow(monday, today)) {
            return@withContext Result.failure(
                TimetableException(TimetableError(TimetableErrorKind.BAD_WEEK)),
            )
        }

        val key = cacheKey(ref, monday)
        val now = Instant.now(clock)
        if (!force) {
            memory[key]?.let { entry ->
                if (Duration.between(entry.fetchedAt, now).toMinutes() < MEMORY_TTL_MINUTES) {
                    return@withContext Result.success(entry.week)
                }
            }
        }

        mutex.withLock {
            val settings = prefs.settings.first()
            val labelHint = ref.displayLabel
                ?: if (ref.isTeacher) dao.teacherLabel(ref.id) else null
            val effectiveRef = ref.withLabel(labelHint)
            val primary = if (settings.dataSourceMode == DataSourceMode.OFFICIAL) official else proxy

            val primaryResult = runCatching {
                NetworkRetry.withRetry { primary.fetchWeek(monday, effectiveRef, now) }
            }

            primaryResult.fold(
                onSuccess = { week ->
                    persist(week)
                    Result.success(week)
                },
                onFailure = { failure ->
                    val mapped = (failure as? TimetableException)?.error ?: errorMapper.map(failure)
                    val fallbackAllowed = settings.officialFallbackEnabled &&
                        primary.mode == DataSourceMode.PROXY &&
                        mapped.kind in FALLBACK_KINDS
                    if (fallbackAllowed) {
                        runCatching {
                            NetworkRetry.withRetry(attempts = 2) {
                                official.fetchWeek(monday, effectiveRef, now)
                            }
                        }.fold(
                            onSuccess = { week ->
                                persist(week)
                                Result.success(week)
                            },
                            onFailure = { fallbackFailure ->
                                Result.failure(TimetableException(mapped, fallbackFailure))
                            },
                        )
                    } else {
                        Result.failure(TimetableException(mapped, failure))
                    }
                },
            )
        }
    }

    override suspend fun search(query: String): Result<List<SearchItem>> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < MIN_QUERY_LENGTH) return@withContext Result.success(emptyList())

            runCatching { NetworkRetry.withRetry { proxy.search(q) } }
                .mapCatching { items ->
                    cacheSearch(items)
                    items
                }
                .recoverCatching { failure ->
                    val cached = dao.offlineSearch("%$q%").map(::toSearchItem)
                    if (cached.isNotEmpty()) {
                        cached
                    } else {
                        val mapped = (failure as? TimetableException)?.error ?: errorMapper.map(failure)
                        throw TimetableException(mapped, failure)
                    }
                }
        }

    override suspend fun recentSearches(limit: Int): List<SearchItem> = withContext(Dispatchers.IO) {
        dao.recentSearches(limit).map(::toSearchItem)
    }

    override suspend fun teacherLabel(teacherId: String): String? = withContext(Dispatchers.IO) {
        dao.teacherLabel(teacherId)
    }

    private suspend fun persist(week: Week) {
        dao.replaceWeek(week.toWeekEntity(), week.toEventEntities(), week.toPersonEntities())
        memory[cacheKey(week.sourceKey, week.monday)] = MemoryEntry(week.fetchedAt, week)
    }

    private suspend fun cacheSearch(items: List<SearchItem>) {
        if (items.isEmpty()) return
        val now = Instant.now(clock).toEpochMilli()
        dao.upsertSearch(items.map { it.toCacheEntity(now) })
        val nowInstant = Instant.now(clock)
        if (Duration.between(lastPrune, nowInstant).toHours() >= PRUNE_EVERY_HOURS) {
            lastPrune = nowInstant
            dao.pruneSearchCache(nowInstant.minus(SEARCH_TTL).toEpochMilli())
        }
    }

    private fun cacheKey(ref: SourceRef, monday: LocalDate): String = "${ref.key}@$monday"

    private fun cacheKey(sourceKey: String, monday: LocalDate): String = "$sourceKey@$monday"

    private fun SearchItem.toCacheEntity(now: Long): SearchCacheEntity = SearchCacheEntity(
        type = if (source.isGroup) "group" else "teacher",
        id = source.id,
        label = label,
        hint = hint,
        faculty = faculty,
        group = group,
        teacherId = teacherId,
        only = true,
        lastSeenAt = now,
    )

    private fun toSearchItem(entity: SearchCacheEntity): SearchItem = SearchItem(
        source = if (entity.type == "teacher") {
            SourceRef.teacher(entity.id, entity.label)
        } else {
            SourceRef.group(entity.id, entity.label)
        },
        label = entity.label,
        hint = entity.hint,
        faculty = entity.faculty,
        group = entity.group,
        teacherId = entity.teacherId,
    )

    private companion object {
        const val MEMORY_TTL_MINUTES = 5L
        const val MIN_QUERY_LENGTH = 2
        const val PRUNE_EVERY_HOURS = 12L
        val SEARCH_TTL: Duration = Duration.ofDays(90)
        val FALLBACK_KINDS = setOf(
            TimetableErrorKind.NETWORK,
            TimetableErrorKind.TIMEOUT,
            TimetableErrorKind.UNAVAILABLE,
            TimetableErrorKind.NOT_FOUND,
        )
    }
}
