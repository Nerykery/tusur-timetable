package ru.tusur.timetable.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.tusur.timetable.data.map.FeetBuildingParser
import ru.tusur.timetable.data.network.ApiErrorMapper
import ru.tusur.timetable.data.network.NetworkRetry
import ru.tusur.timetable.data.network.ProxyRemoteSource
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.BuildingMap
import ru.tusur.timetable.domain.model.TimetableException
import ru.tusur.timetable.domain.repository.BuildingRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBuildingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proxy: ProxyRemoteSource,
    private val parser: FeetBuildingParser,
    private val errorMapper: ApiErrorMapper,
    private val clock: Clock,
) : BuildingRepository {

    private val mapMutex = Mutex()
    @Volatile
    private var mapCache: BuildingMap? = null

    @Volatile
    private var loadCache: Pair<Instant, BuildingLoad>? = null

    override suspend fun map(): BuildingMap = withContext(Dispatchers.IO) {
        mapCache ?: mapMutex.withLock {
            mapCache ?: readMap().also { mapCache = it }
        }
    }

    override suspend fun load(): Result<BuildingLoad> = withContext(Dispatchers.IO) {
        val cached = loadCache
        if (cached != null && Duration.between(cached.first, Instant.now(clock)).toMinutes() < LOAD_TTL_MINUTES) {
            return@withContext Result.success(cached.second)
        }
        runCatching { NetworkRetry.withRetry { proxy.load() } }
            .onSuccess { loadCache = Instant.now(clock) to it }
            .recoverCatching { throwable ->
                throw TimetableException(errorMapper.map(throwable), throwable)
            }
    }

    private fun readMap(): BuildingMap = runCatching {
        val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        parser.parse(raw)
    }.getOrElse {
        BuildingMap(
            title = "ФЭТ",
            address = "ул. Вершинина, 74",
            building = "ф",
            floors = (0..4).toList(),
            hallFloors = setOf(1),
            roomsByFloor = emptyMap(),
        )
    }

    private companion object {
        const val ASSET = "fet.json"
        const val LOAD_TTL_MINUTES = 5L
    }
}
