package com.example.wppsticker.ui.videoeditor

import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Converts the flexible 8-bit YUV output exposed by MediaCodec into opaque ARGB pixels. */
internal object Yuv420FrameConverter {
    data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)
    data class Frame(val width: Int, val height: Int, val pixels: IntArray)

    /**
     * [colorStandard] and [colorRange] use the corresponding Android MediaFormat values.
     * Image plane buffers may have row padding, interleaved chroma, and non-zero positions.
     */
    fun toArgb(
        imageWidth: Int,
        imageHeight: Int,
        cropLeft: Int,
        cropTop: Int,
        cropRight: Int,
        cropBottom: Int,
        y: Plane,
        u: Plane,
        v: Plane,
        colorStandard: Int,
        colorRange: Int,
        maxOutputDimension: Int = Int.MAX_VALUE
    ): Frame {
        require(imageWidth > 0 && imageHeight > 0)
        require(cropLeft >= 0 && cropTop >= 0 && cropRight <= imageWidth && cropBottom <= imageHeight)
        val width = cropRight - cropLeft
        val height = cropBottom - cropTop
        require(width > 0 && height > 0) { "O decoder retornou um retângulo de corte vazio." }
        require(maxOutputDimension > 0)
        listOf(y, u, v).forEach { plane ->
            require(plane.rowStride > 0 && plane.pixelStride > 0) { "O decoder retornou strides YUV inválidos." }
        }

        // MediaFormat values: BT.709 = 1, BT.601 PAL/NTSC = 2/4, BT.2020 = 6.
        val bt709 = colorStandard == 1
        val bt2020 = colorStandard == 6
        val fullRange = colorRange == 1 // MediaFormat.COLOR_RANGE_FULL = 1.
        val (rv, gu, gv, bu) = when {
            fullRange && bt709 -> floatArrayOf(1.5748f, -0.187324f, -0.468124f, 1.8556f)
            fullRange && bt2020 -> floatArrayOf(1.4746f, -0.164553f, -0.571353f, 1.8814f)
            fullRange -> floatArrayOf(1.402f, -0.344136f, -0.714136f, 1.772f)
            bt709 -> floatArrayOf(1.792741f, -0.213249f, -0.532909f, 2.112402f)
            bt2020 -> floatArrayOf(1.6787f, -0.1873f, -0.6504f, 2.1418f)
            else -> floatArrayOf(1.596027f, -0.391762f, -0.812968f, 2.017232f)
        }
        val lumaScale = if (fullRange) 1f else 1.164384f
        val lumaOffset = if (fullRange) 0f else 16f
        // The sticker canvas is 512 px. Keeping a 2x source frame is enough for clean
        // transforms while avoiding 64+ MiB ARGB allocations for 4K input frames.
        val sampleSize = ceil(maxOf(width, height).toDouble() / maxOutputDimension).toInt().coerceAtLeast(1)
        val outputWidth = (width + sampleSize - 1) / sampleSize
        val outputHeight = (height + sampleSize - 1) / sampleSize
        val pixels = IntArray(outputWidth * outputHeight)
        var target = 0
        for (row in 0 until outputHeight) {
            val sourceTop = cropTop + row * sampleSize
            val sourceBottom = minOf(cropBottom, sourceTop + sampleSize)
            for (column in 0 until outputWidth) {
                val sourceLeft = cropLeft + column * sampleSize
                val sourceRight = minOf(cropRight, sourceLeft + sampleSize)
                val blockWidth = sourceRight - sourceLeft
                val blockHeight = sourceBottom - sourceTop
                // A 2x2 stratified box filter avoids an O(input-pixels) scan for every
                // output pixel when reducing 4K frames. At most four taps are converted.
                val tapsX = minOf(2, blockWidth)
                val tapsY = minOf(2, blockHeight)
                var ySum = 0
                var uSum = 0
                var vSum = 0
                var sampleCount = 0
                for (tapY in 0 until tapsY) {
                    val sourceY = sourceTop + ((tapY * 2 + 1) * blockHeight) / (tapsY * 2)
                    for (tapX in 0 until tapsX) {
                        val sourceX = sourceLeft + ((tapX * 2 + 1) * blockWidth) / (tapsX * 2)
                        ySum += sample(y, sourceY, sourceX)
                        uSum += sample(u, sourceY / 2, sourceX / 2)
                        vSum += sample(v, sourceY / 2, sourceX / 2)
                        sampleCount++
                    }
                }
                val luma = (ySum.toFloat() / sampleCount - lumaOffset) * lumaScale
                val cb = uSum.toFloat() / sampleCount - 128f
                val cr = vSum.toFloat() / sampleCount - 128f
                val red = (luma + rv * cr).roundToInt().coerceIn(0, 255)
                val green = (luma + gu * cb + gv * cr).roundToInt().coerceIn(0, 255)
                val blue = (luma + bu * cb).roundToInt().coerceIn(0, 255)
                pixels[target++] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Frame(outputWidth, outputHeight, pixels)
    }

    private fun sample(plane: Plane, row: Int, column: Int): Int {
        val buffer = plane.buffer
        val index = buffer.position().toLong() + row.toLong() * plane.rowStride + column.toLong() * plane.pixelStride
        require(index >= buffer.position() && index < buffer.limit()) {
            "O decoder retornou um plano YUV truncado ou incompatível com os strides informados."
        }
        return buffer.get(index.toInt()).toInt() and 0xFF
    }
}
