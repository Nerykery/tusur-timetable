package ru.tusur.timetable.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.tusur.timetable.ui.schedule.ClockTicker
import ru.tusur.timetable.ui.schedule.DefaultClockTicker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClockTicker(): ClockTicker = DefaultClockTicker()
}
