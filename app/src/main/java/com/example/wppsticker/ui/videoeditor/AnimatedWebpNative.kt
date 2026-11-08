package com.example.wppsticker.ui.videoeditor

import android.graphics.Bitmap

internal object AnimatedWebpNative {
    init { System.loadLibrary("animated_webp") }

    external fun create(width: Int, height: Int, quality: Float, method: Int = 4): Long
    external fun addFrame(handle: Long, bitmap: Bitmap, timestampMs: Int): Boolean
    external fun finish(handle: Long, outputPath: String, durationMs: Int): Boolean
    external fun abort(handle: Long)
    external fun encodeStaticTwoFrames(bitmap: Bitmap, outputPath: String, frameDurationMs: Int = 500): Boolean
    external fun isValidAnimatedWebp(path: String): Boolean
    external fun decodeFirstFrame(path: String): Bitmap?
}
