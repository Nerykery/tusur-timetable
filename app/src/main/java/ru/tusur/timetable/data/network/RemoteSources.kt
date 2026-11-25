package ru.tusur.timetable.data.network

import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.data.mapper.OfficialWeekMapper
import ru.tusur.timetable.data.mapper.ProxyResponseMapper
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.DataSourceMode
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.domain.model.TimetableException
import ru.tusur.timetable.domain.model.Week
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Источник расписания. Реализации взаимозаменяемы, UI о них не знает. */
interface ScheduleRemoteSource {
    val mode: DataSourceMode
    suspend fun fetchWeek(monday: LocalDate, ref: SourceRef, fetchedAt: Instant): Week
}

interface SearchRemoteSource {
    suspend fun search(query: String): List<SearchItem>
}

interface BuildingLoadRemoteSource {
    suspend fun load(): BuildingLoad
}

/** Основной источник — прокси-API сайта расписания. */
@Singleton
class ProxyRemoteSource @Inject constructor(
    private val api: ProxyTimetableApi,
    private val mapper: ProxyResponseMapper,
) : ScheduleRemoteSource, SearchRemoteSource, BuildingLoadRemoteSource {

    override val mode: DataSourceMode = DataSourceMode.PROXY

    override suspend fun fetchWeek(monday: LocalDate, ref: SourceRef, fetchedAt: Instant): Week {
        val dto = api.week(
            monday = monday.toString(),
            group = ref.groupQuery,
            teacher = ref.teacherQuery,
        )
        return mapper.mapWeek(dto, ref, fetchedAt)
    }

    override suspend fun search(query: String): List<SearchItem> = mapper.mapSearch(api.search(query))

    override suspend fun load(): BuildingLoad = mapper.mapLoad(api.load())
}

/**
 * Резервный источник — официальный публичный API ТУСУР (api.tusur.ru).
 * Не включён по умолчанию: расписания группы хватает id группы (NNN-N),
 * преподавателя официальный API ищет по фамилии, отдельного поиска у него нет.
 */
@Singleton
class OfficialRemoteSource @Inject constructor(
    private val api: OfficialTusurApi,
) : ScheduleRemoteSource {

    override val mode: DataSourceMode = DataSourceMode.OFFICIAL

    private val parityCache = ConcurrentHashMap<String, Pair<Instant, String?>>()

    override suspend fun fetchWeek(monday: LocalDate, ref: SourceRef, fetchedAt: Instant): Week {
        val from = monday.toString()
        val to = monday.plusDays(6).toString()

        val response = when (ref.type) {
            SourceRef.Type.GROUP -> {
                val group = ref.officialGroupId
                    ?: throw TimetableException(TimetableError(TimetableErrorKind.BAD_WEEK))
                api.lessons(group = group, dateFrom = from, dateTo = to)
            }

            SourceRef.Type.TEACHER -> {
                val name = ref.officialTeacherName
                    ?: throw TimetableException(TimetableError(TimetableErrorKind.UNKNOWN_TARGET))
                api.lecturer(dateFrom = from, dateTo = to, name = name)
            }
        }

        val parity = if (ref.isGroup) parityFor(ref.officialGroupId, monday, fetchedAt) else null

        return OfficialWeekMapper.map(
            days = response.data,
            ref = ref,
            monday = monday,
            parity = parity,
            fetchedAt = fetchedAt,
            today = TomskTime.today(),
        )
    }

    private suspend fun parityFor(group: String?, monday: LocalDate, now: Instant): String? {
        if (group.isNullOrBlank()) return null
        val cached = parityCache[group]
        if (cached != null && cached.first.plusSeconds(6 * 3600).isAfter(now)) return cached.second
        val parity = runCatching {
            api.weeks(group).data
                .firstOrNull { TomskTime.parseDate(it.startsAt) == monday }
                ?.parity
        }.getOrNull()
        parityCache[group] = now to parity
        return parity
    }
}
