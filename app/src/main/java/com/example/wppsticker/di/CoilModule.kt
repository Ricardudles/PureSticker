package com.example.wppsticker.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            // Disable disk cache - all our images are local files already.
            // This prevents duplicating the stickers on the user's storage.
            .diskCache(null) 
            .memoryCache {
                MemoryCache.Builder(context)
                    // Set a fixed memory cache size (e.g., 32MB) to have a predictable memory footprint.
                    .maxSizePercent(0.15) // Use 15% of app's available memory
                    .build()
            }
            .respectCacheHeaders(false) // For local files, we don't need to check http headers
            .build()
    }
}
