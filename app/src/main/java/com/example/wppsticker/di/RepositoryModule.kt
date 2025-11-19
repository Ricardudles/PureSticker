package com.example.wppsticker.di

import com.example.wppsticker.data.repository.StickerRepositoryImpl
import com.example.wppsticker.domain.repository.StickerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStickerRepository(impl: StickerRepositoryImpl): StickerRepository
}
