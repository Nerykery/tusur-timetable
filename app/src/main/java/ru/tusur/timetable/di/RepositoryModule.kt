package ru.tusur.timetable.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.tusur.timetable.data.repository.DefaultBuildingRepository
import ru.tusur.timetable.data.repository.OfflineFirstTimetableRepository
import ru.tusur.timetable.domain.repository.BuildingRepository
import ru.tusur.timetable.domain.repository.TimetableRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTimetableRepository(
        repository: OfflineFirstTimetableRepository,
    ): TimetableRepository

    @Binds
    @Singleton
    abstract fun bindBuildingRepository(
        repository: DefaultBuildingRepository,
    ): BuildingRepository
}
