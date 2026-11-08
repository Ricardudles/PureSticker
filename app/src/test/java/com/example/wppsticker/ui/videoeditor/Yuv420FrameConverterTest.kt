package com.example.wppsticker.ui.videoeditor

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Yuv420FrameConverterTest {
    @Test
    fun convertsLimitedRangeBlackAndWhiteWithPaddedRows() {
        val pixels = convert(
            width = 2,
            height = 2,
            y = plane(byteArrayOf(16, 235.toByte(), 99, 235.toByte(), 16, 99), rowStride = 3),
            u = plane(byteArrayOf(128.toByte(), 0), rowStride = 2),
            v = plane(byteArrayOf(128.toByte(), 0), rowStride = 2)
        ).pixels

        assertEquals(0xFF000000.toInt(), pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), pixels[1])
        assertEquals(0xFFFFFFFF.toInt(), pixels[2])
        assertEquals(0xFF000000.toInt(), pixels[3])
    }

    @Test
    fun supportsInterleavedChromaPlanesWithIndependentPositions() {
        val chroma = ByteBuffer.wrap(byteArrayOf(128.toByte(), 128.toByte(), 0, 0))
        val u = chroma.duplicate().apply { position(0) }
        val v = chroma.duplicate().apply { position(1) }
        val pixels = convert(
            width = 2,
            height = 2,
            y = plane(byteArrayOf(76, 76, 76, 76), rowStride = 2),
            u = Yuv420FrameConverter.Plane(u, rowStride = 4, pixelStride = 2),
            v = Yuv420FrameConverter.Plane(v, rowStride = 4, pixelStride = 2)
        ).pixels

        assertEquals(List(4) { 0xFF464646.toInt() }, pixels.toList())
    }

    @Test
    fun honorsImageCropAndBt709FullRangeMetadata() {
        val frame = Yuv420FrameConverter.toArgb(
            imageWidth = 4,
            imageHeight = 2,
            cropLeft = 2,
            cropTop = 0,
            cropRight = 4,
            cropBottom = 2,
            y = plane(byteArrayOf(0, 25, 100.toByte(), 150.toByte(), 0, 25, 100.toByte(), 150.toByte()), rowStride = 4),
            u = plane(byteArrayOf(128.toByte(), 128.toByte()), rowStride = 2),
            v = plane(byteArrayOf(128.toByte(), 128.toByte()), rowStride = 2),
            colorStandard = BT709,
            colorRange = FULL_RANGE
        )

        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertEquals(4, frame.pixels.size)
        assertEquals(0xFF646464.toInt(), frame.pixels[0])
        assertEquals(0xFF969696.toInt(), frame.pixels[1])
        assertEquals(0xFF646464.toInt(), frame.pixels[2])
        assertEquals(0xFF969696.toInt(), frame.pixels[3])
    }

    @Test
    fun rejectsTruncatedPlaneInsteadOfReadingOutsideTheBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            convert(
                width = 2,
                height = 2,
                y = plane(byteArrayOf(16, 16, 16), rowStride = 2),
                u = plane(byteArrayOf(128.toByte()), rowStride = 1),
                v = plane(byteArrayOf(128.toByte()), rowStride = 1)
            )
        }
    }

    @Test
    fun downsamplesLargeFramesBeforeAllocatingArgbPixels() {
        val y = ByteArray(16) { index -> if (index % 4 < 2) 16 else 235.toByte() }
        val frame = Yuv420FrameConverter.toArgb(
            imageWidth = 4,
            imageHeight = 4,
            cropLeft = 0,
            cropTop = 0,
            cropRight = 4,
            cropBottom = 4,
            y = plane(y, rowStride = 4),
            u = plane(ByteArray(4) { 128.toByte() }, rowStride = 2),
            v = plane(ByteArray(4) { 128.toByte() }, rowStride = 2),
            colorStandard = BT601,
            colorRange = FULL_RANGE,
            maxOutputDimension = 2
        )

        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertEquals(listOf(0xFF101010.toInt(), 0xFFEBEBEB.toInt(), 0xFF101010.toInt(), 0xFFEBEBEB.toInt()), frame.pixels.toList())
    }

    @Test
    fun usesStratifiedFourTapAverageForLargeReductionBlocks() {
        val y = ByteArray(16) { index -> if (index % 4 < 2) 16 else 235.toByte() }
        val frame = Yuv420FrameConverter.toArgb(
            imageWidth = 4,
            imageHeight = 4,
            cropLeft = 0,
            cropTop = 0,
            cropRight = 4,
            cropBottom = 4,
            y = plane(y, rowStride = 4),
            u = plane(ByteArray(4) { 128.toByte() }, rowStride = 2),
            v = plane(ByteArray(4) { 128.toByte() }, rowStride = 2),
            colorStandard = BT601,
            colorRange = FULL_RANGE,
            maxOutputDimension = 1
        )

        assertEquals(1, frame.width)
        assertEquals(1, frame.height)
        assertEquals(0xFF7E7E7E.toInt(), frame.pixels.single())
    }

    private fun convert(width: Int, height: Int, y: Yuv420FrameConverter.Plane,
                        u: Yuv420FrameConverter.Plane, v: Yuv420FrameConverter.Plane): Yuv420FrameConverter.Frame =
        Yuv420FrameConverter.toArgb(
            imageWidth = width,
            imageHeight = height,
            cropLeft = 0,
            cropTop = 0,
            cropRight = width,
            cropBottom = height,
            y = y,
            u = u,
            v = v,
            colorStandard = BT601,
            colorRange = LIMITED_RANGE
        )

    private fun plane(bytes: ByteArray, rowStride: Int) =
        Yuv420FrameConverter.Plane(ByteBuffer.wrap(bytes), rowStride, pixelStride = 1)

    private companion object {
        const val BT601 = 4 // MediaFormat.COLOR_STANDARD_BT601_NTSC
        const val BT709 = 1 // MediaFormat.COLOR_STANDARD_BT709
        const val FULL_RANGE = 1 // MediaFormat.COLOR_RANGE_FULL
        const val LIMITED_RANGE = 2 // MediaFormat.COLOR_RANGE_LIMITED
    }
}
