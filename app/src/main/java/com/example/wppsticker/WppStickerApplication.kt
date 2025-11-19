package com.example.wppsticker

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WppStickerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("StickerAppDebug", "WppStickerApplication onCreate called. App process started.")
    }
}
