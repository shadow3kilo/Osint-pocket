package com.osintpocket.app.di

import android.content.Context
import androidx.room.Room
import com.osintpocket.app.data.history.AppDatabase
import com.osintpocket.app.data.history.HistoryDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "osint_pocket.db").build()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.history()
}
