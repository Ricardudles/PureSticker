package com.example.wppsticker

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.wppsticker.util.CacheCleaner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WppStickerApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override fun onCreate() {
        // Setup global exception handler immediately to catch startup crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("StickerAppCrash", "FATAL EXCEPTION on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            super.onCreate()
            Log.d("StickerAppDebug", "WppStickerApplication onCreate called. App process started.")
            
            // Clean up temporary files in background safely
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CacheCleaner.cleanVideoCache(applicationContext)
                } catch (e: Exception) {
                    Log.e("StickerAppDebug", "Failed to clean cache", e)
                }
            }
        } catch (e: Exception) {
            Log.e("StickerAppCrash", "Error during Application.onCreate", e)
        }
    }

    /**
     * This is the recommended way by Coil to provide a singleton ImageLoader instance.
     * Hilt injects the loader we configured in CoilModule.kt.
     */
    override fun newImageLoader(): ImageLoader {
        return try {
            // Safely attempt to get the injected ImageLoader
            imageLoader.get()
        } catch (e: Exception) {
            Log.e("StickerAppCrash", "Failed to get injected ImageLoader, creating fallback", e)
            // Fallback to a basic ImageLoader if injection fails
            ImageLoader.Builder(this).build()
        }
    }
}
