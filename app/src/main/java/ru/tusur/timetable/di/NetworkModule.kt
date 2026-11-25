package ru.tusur.timetable.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.tusur.timetable.BuildConfig
import ru.tusur.timetable.data.network.NetworkRetry
import ru.tusur.timetable.data.network.OfficialTusurApi
import ru.tusur.timetable.data.network.ProxyTimetableApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetworkRetry.loggingClient(BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideProxyApi(client: OkHttpClient, json: Json): ProxyTimetableApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.TIMETABLE_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ProxyTimetableApi::class.java)

    @Provides
    @Singleton
    fun provideOfficialApi(client: OkHttpClient, json: Json): OfficialTusurApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.OFFICIAL_TUSUR_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OfficialTusurApi::class.java)
}
