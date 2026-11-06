package com.example.wppsticker.ui.videoeditor

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AnimatedWebpNativeTest {
    @Test
    fun duplicateStaticFramesRemainAnimatedAndDecode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.TRANSPARENT)
            for (y in 120..390) for (x in 80..420) setPixel(x, y, android.graphics.Color.RED)
        }
        val output = File(context.cacheDir, "duplicate-frame-test.webp")
        try {
            assertTrue(AnimatedWebpNative.encodeStaticTwoFrames(bitmap, output.absolutePath))
            assertTrue(output.length() in 1..512_000L)
            assertTrue(AnimatedWebpNative.isValidAnimatedWebp(output.absolutePath))
            assertTrue(AnimatedWebpNative.decodeFirstFrame(output.absolutePath) != null)
        } finally {
            bitmap.recycle()
            output.delete()
        }
    }

    @Test
    fun animationEncoderProducesValidatedWebp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val second = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        val output = File(context.cacheDir, "animation-encoder-test.webp")
        var encoder = AnimatedWebpNative.create(512, 512, 85f)
        try {
            assertTrue(encoder != 0L)
            assertTrue(AnimatedWebpNative.addFrame(encoder, first, 0))
            assertTrue(AnimatedWebpNative.addFrame(encoder, second, 100))
            assertTrue(AnimatedWebpNative.finish(encoder, output.absolutePath, 200))
            encoder = 0L
            assertTrue(output.length() in 1..512_000L)
            assertTrue(AnimatedWebpNative.isValidAnimatedWebp(output.absolutePath))
            assertTrue(AnimatedWebpNative.decodeFirstFrame(output.absolutePath) != null)
        } finally {
            if (encoder != 0L) AnimatedWebpNative.abort(encoder)
            first.recycle()
            second.recycle()
            output.delete()
        }
    }

    @Test
    fun validatorAcceptsEightMillisecondsAndTenSecondsOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        val second = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.YELLOW)
        }
        fun encode(secondTimestamp: Int, duration: Int, name: String): Boolean {
            val output = File(context.cacheDir, name)
            var handle = AnimatedWebpNative.create(512, 512, 80f)
            try {
                assertTrue(AnimatedWebpNative.addFrame(handle, first, 0))
                assertTrue(AnimatedWebpNative.addFrame(handle, second, secondTimestamp))
                assertTrue(AnimatedWebpNative.finish(handle, output.absolutePath, duration))
                handle = 0L
                return AnimatedWebpNative.isValidAnimatedWebp(output.absolutePath)
            } finally {
                if (handle != 0L) AnimatedWebpNative.abort(handle)
                output.delete()
            }
        }
        try {
            assertTrue(encode(8, 16, "duration-8ms.webp"))
            assertTrue(!encode(7, 16, "duration-7ms.webp"))
            assertTrue(encode(9_992, 10_000, "duration-10s.webp"))
            assertTrue(!encode(9_992, 10_001, "duration-10s-plus-1.webp"))
        } finally {
            first.recycle()
            second.recycle()
        }
    }
}
