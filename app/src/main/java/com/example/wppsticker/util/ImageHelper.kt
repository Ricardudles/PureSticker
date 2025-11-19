package com.example.wppsticker.util

import android.graphics.Bitmap

object ImageHelper {
    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, false)
    }
}
