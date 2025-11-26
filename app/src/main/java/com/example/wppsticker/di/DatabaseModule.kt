package com.example.wppsticker.di

import android.content.Context
import androidx.room.Room
import com.example.wppsticker.data.local.AppDatabase
import com.example.wppsticker.data.local.StickerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wppsticker.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideStickerDao(database: AppDatabase): StickerDao {
        return database.stickerDao()
    }
}
