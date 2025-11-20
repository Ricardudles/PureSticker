package com.example.wppsticker

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WppStickerApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override fun onCreate() {
        super.onCreate()
        Log.d("StickerAppDebug", "WppStickerApplication onCreate called. App process started.")
    }

    /**
     * This is the recommended way by Coil to provide a singleton ImageLoader instance.
     * Hilt injects the loader we configured in CoilModule.kt.
     */
    override fun newImageLoader(): ImageLoader {
        return imageLoader.get()
    }
}
