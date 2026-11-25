package ru.tusur.timetable.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import ru.tusur.timetable.data.network.dto.LoadDto
import ru.tusur.timetable.data.network.dto.OfficialLessonsResponse
import ru.tusur.timetable.data.network.dto.OfficialWeeksResponse
import ru.tusur.timetable.data.network.dto.SearchDto
import ru.tusur.timetable.data.network.dto.WeekDto

/** Прокси-API сайта расписания (базовый URL — BuildConfig.TIMETABLE_BASE_URL). */
interface ProxyTimetableApi {

    @GET("api/week")
    suspend fun week(
        @Query("monday") monday: String,
        @Query("group") group: String? = null,
        @Query("teacher") teacher: String? = null,
    ): WeekDto

    @GET("api/search")
    suspend fun search(@Query("q") query: String): SearchDto

    @GET("api/load")
    suspend fun load(): LoadDto
}

/** Официальный публичный API ТУСУР (базовый URL — https://api.tusur.ru/). */
interface OfficialTusurApi {

    @GET("api/v1/timetable/lessons/{group}")
    suspend fun lessons(
        @Path("group") group: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
    ): OfficialLessonsResponse

    @GET("api/v1/timetable/lessons/lecturer")
    suspend fun lecturer(
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("name") name: String,
    ): OfficialLessonsResponse

    @GET("api/v1/timetable/weeks/{group}")
    suspend fun weeks(@Path("group") group: String): OfficialWeeksResponse
}
