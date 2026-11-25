package ru.tusur.timetable.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.tusur.timetable.data.db.TimetableDao
import ru.tusur.timetable.data.db.TusurDatabase
import ru.tusur.timetable.data.db.WidgetDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TusurDatabase =
        Room.databaseBuilder(context, TusurDatabase::class.java, "tusur.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTimetableDao(database: TusurDatabase): TimetableDao = database.timetableDao()

    @Provides
    fun provideWidgetDao(database: TusurDatabase): WidgetDao = database.widgetDao()
}
