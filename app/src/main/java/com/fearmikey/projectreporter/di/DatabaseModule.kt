package com.fearmikey.projectreporter.di

import android.content.Context
import androidx.room.Room
import com.fearmikey.projectreporter.data.dao.ReportDao
import com.fearmikey.projectreporter.data.db.ReportDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReportDatabase {
        return Room.databaseBuilder(
            context,
            ReportDatabase::class.java,
            "report_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideReportDao(database: ReportDatabase): ReportDao {
        return database.reportDao()
    }
}
