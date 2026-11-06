package com.example.wppsticker.ui.videoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.example.wppsticker.ui.editor.TextData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.yield
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class AnimatedExportStage { PREPARING, DECODING, ENCODING, VALIDATING }

data class AnimatedExportProgress(
    val stage: AnimatedExportStage = AnimatedExportStage.PREPARING,
    val attempt: Int = 0,
    val totalAttempts: Int = 0,
    val frame: Int = 0,
    val totalFrames: Int = 0,
    val sourceTimeMs: Long = 0L,
    val frameRate: Int = 0,
    val quality: Int = 0,
    val elapsedMs: Long = 0L
)

data class AnimatedExportFailure(val message: String, val debugDetails: String? = null)

private class AnimatedExportTimeoutException(message: String) : IllegalStateException(message)

enum class AnimatedExportProfile { FLUID, BALANCED, SHARP }

internal data class AnimatedExportResult(val file: File, val frameRate: Int, val quality: Int) {
    val sizeBytes: Long get() = file.length()
}

internal object AnimatedStickerExporter {
    const val MAX_BYTES = 512_000L
    const val MAX_DURATION_MS = 10_000L
    const val MAX_EXPORT_TIME_MS = 180_000L

    suspend fun export(
        context: Context,
        source: Uri,
        isGif: Boolean,
        trimStartMs: Long,
        trimEndMs: Long,
        crop: VideoCropState,
        texts: List<TextData>,
        profile: AnimatedExportProfile,
        outputDirectory: File,
        onProgress: (AnimatedExportProgress) -> Unit,
        isCancelled: () -> Boolean
    ): AnimatedExportResult {
        val exportStartedAt = SystemClock.elapsedRealtime()
        val deadline = exportStartedAt + MAX_EXPORT_TIME_MS
        val start = trimStartMs.coerceAtLeast(0L)
        val end = trimEndMs.coerceAtLeast(start + 8L).coerceAtMost(start + MAX_DURATION_MS)
        require(end - start >= 16L) { "Selecione um trecho de pelo menos 16 ms." }

        onProgress(AnimatedExportProgress(stage = AnimatedExportStage.PREPARING))
        val gif = if (isGif) GifSource(context, source) else null
        try {
            val videoInfo = if (gif == null) readVideoInfo(context, source) else null
            val sourceFps = (gif?.frameRate ?: videoInfo!!.frameRate).coerceIn(8f, 125f)
            val rates = listOf(1f, .8f, .6f, .4f, .25f)
                .map { (sourceFps * it).toInt().coerceIn(8, 125) }
                .distinct().sortedDescending()
            val qualities = listOf(95, 85, 75, 65, 55, 45, 35)
            val attempts = (rates.flatMap { fps -> qualities.map { q -> fps to q } }).let { pairs ->
                when (profile) {
                    AnimatedExportProfile.FLUID -> pairs.sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
                    AnimatedExportProfile.SHARP -> pairs.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
                    AnimatedExportProfile.BALANCED -> pairs.sortedWith(
                        compareByDescending<Pair<Int, Int>> { (it.first / sourceFps) * (it.second / 100f) }
                            .thenByDescending { it.second }.thenByDescending { it.first }
                    )
                }
            }
            val tempFiles = mutableListOf<File>()
            try {
                attempts.forEachIndexed { index, (fps, quality) ->
                    checkExportState(isCancelled, deadline)
                    val intervalMs = max(8, 1000 / fps)
                    val targetFrameCount = ceil((end - start) / intervalMs.toDouble()).toInt().coerceAtLeast(2)
                    onProgress(
                        AnimatedExportProgress(
                            stage = AnimatedExportStage.DECODING,
                            attempt = index + 1,
                            totalAttempts = attempts.size,
                            totalFrames = targetFrameCount,
                            frameRate = fps,
                            quality = quality,
                            elapsedMs = SystemClock.elapsedRealtime() - exportStartedAt
                        )
                    )
                    val candidate = File(outputDirectory, "animated_candidate_${System.nanoTime()}.webp")
                    tempFiles += candidate
                    val ok = encodeCandidate(
                        context = context,
                        source = source,
                        videoInfo = videoInfo,
                        gif = gif,
                        startMs = start,
                        endMs = end,
                        fps = fps,
                        quality = quality,
                        crop = crop,
                        texts = texts,
                        output = candidate,
                        attempt = index + 1,
                        attemptCount = attempts.size,
                        targetFrameCount = targetFrameCount,
                        exportStartedAt = exportStartedAt,
                        deadline = deadline,
                        onProgress = onProgress,
                        isCancelled = isCancelled
                    )
                    onProgress(
                        AnimatedExportProgress(
                            stage = AnimatedExportStage.VALIDATING,
                            attempt = index + 1,
                            totalAttempts = attempts.size,
                            frame = targetFrameCount,
                            totalFrames = targetFrameCount,
                            frameRate = fps,
                            quality = quality,
                            elapsedMs = SystemClock.elapsedRealtime() - exportStartedAt
                        )
                    )
                    if (ok && candidate.length() <= MAX_BYTES && AnimatedWebpNative.isValidAnimatedWebp(candidate.absolutePath)) {
                        tempFiles.remove(candidate)
                        return AnimatedExportResult(candidate, fps, quality)
                    }
                    candidate.delete()
                }
            } finally {
                tempFiles.forEach { it.delete() }
            }
            throw IllegalStateException("Não foi possível gerar uma figurinha válida abaixo de 500 KiB. Encurte o trecho e tente novamente.")
        } finally {
            gif?.close()
        }
    }

    private data class VideoInfo(
        val mime: String,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val frameRate: Float
    )

    private fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalArgumentException("O arquivo não contém uma faixa de vídeo compatível.")
            val format = extractor.getTrackFormat(videoTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Não foi possível identificar o codec de vídeo.")
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= MAX_VIDEO_PIXELS) {
                "O vídeo excede o limite de 4096 px por lado ou 16 megapixels."
            }
            val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
            } else {
                30f
            }
            return VideoInfo(
                mime = mime,
                width = width,
                height = height,
                rotation = format.integerOrDefault(MediaFormat.KEY_ROTATION, 0),
                frameRate = frameRate.coerceIn(8f, 125f)
            )
        } finally {
            extractor.release()
        }
    }

    private suspend fun encodeCandidate(
        context: Context,
        source: Uri,
        videoInfo: VideoInfo?,
        gif: GifSource?,
        startMs: Long,
        endMs: Long,
        fps: Int,
        quality: Int,
        crop: VideoCropState,
        texts: List<TextData>,
        output: File,
        attempt: Int,
        attemptCount: Int,
        targetFrameCount: Int,
        exportStartedAt: Long,
        deadline: Long,
        onProgress: (AnimatedExportProgress) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean {
        val interval = max(8, 1000 / fps)
        val duration = (endMs - startMs).toInt()
        val frameCount = ceil(duration / interval.toDouble()).toInt().coerceAtLeast(2)
        var handle = AnimatedWebpNative.create(512, 512, quality.toFloat())
        if (handle == 0L) throw IllegalStateException("Não foi possível iniciar o encoder WebP nativo.")
        var added = 0
        try {
            if (gif != null) {
                return encodeGifCandidate(
                    gif, startMs, duration, interval, frameCount, handle, output, crop, texts,
                    fps, quality, attempt, attemptCount, targetFrameCount, exportStartedAt,
                    deadline, onProgress, isCancelled
                ).also { handle = 0L }
            }
            return encodeVideoCandidate(
                context, source, videoInfo ?: error("Metadados do vídeo ausentes"), startMs, endMs,
                duration, interval, frameCount, handle, output, crop, texts, fps, quality,
                attempt, attemptCount, targetFrameCount, exportStartedAt, deadline,
                onProgress, isCancelled
            ).also { handle = 0L }
        } catch (failure: Throwable) {
            Log.e("AnimatedStickerExporter", "Candidate $attempt failed before native encoder cleanup", failure)
            throw failure
        } finally {
            if (handle != 0L) AnimatedWebpNative.abort(handle)
        }
    }

    private suspend fun encodeGifCandidate(
        gif: GifSource,
        startMs: Long,
        duration: Int,
        intervalMs: Int,
        frameCount: Int,
        handle: Long,
        output: File,
        crop: VideoCropState,
        texts: List<TextData>,
        fps: Int,
        quality: Int,
        attempt: Int,
        attemptCount: Int,
        targetFrameCount: Int,
        exportStartedAt: Long,
        deadline: Long,
        onProgress: (AnimatedExportProgress) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean {
        var framesWritten = 0
        var lastUpdateAt = 0L
        for (index in 0 until frameCount) {
            checkExportState(isCancelled, deadline)
            val relativeTimestamp = index * intervalMs
            if (relativeTimestamp >= duration) break
            val sourceBitmap = gif.frameAt(startMs + relativeTimestamp)
            val normalized = render(sourceBitmap, crop, texts)
            if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
            val added = try {
                AnimatedWebpNative.addFrame(handle, normalized, relativeTimestamp)
            } finally {
                normalized.recycle()
            }
            if (!added) throw IllegalStateException("O encoder recusou o quadro ${framesWritten + 1} do GIF.")
            framesWritten++
            lastUpdateAt = reportFrameProgress(
                onProgress, lastUpdateAt, framesWritten, targetFrameCount, relativeTimestamp.toLong(),
                fps, quality, attempt, attemptCount, exportStartedAt, AnimatedExportStage.ENCODING
            )
            if (index % 3 == 0) yield()
        }
        if (framesWritten < 2) return false
        checkExportState(isCancelled, deadline)
        return AnimatedWebpNative.finish(handle, output.absolutePath, duration) &&
            output.isFile && output.length() > 0L
    }

    private suspend fun encodeVideoCandidate(
        context: Context,
        source: Uri,
        videoInfo: VideoInfo,
        startMs: Long,
        endMs: Long,
        duration: Int,
        intervalMs: Int,
        frameCount: Int,
        handle: Long,
        output: File,
        crop: VideoCropState,
        texts: List<TextData>,
        fps: Int,
        quality: Int,
        attempt: Int,
        attemptCount: Int,
        targetFrameCount: Int,
        exportStartedAt: Long,
        deadline: Long,
        onProgress: (AnimatedExportProgress) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean {
        val extractor = MediaExtractor()
        var imageReader: ImageReader? = null
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) == videoInfo.mime
            } ?: throw IllegalArgumentException("A faixa de vídeo foi removida ou não pode mais ser acessada.")
            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            imageReader = ImageReader.newInstance(
                videoInfo.width,
                videoInfo.height,
                PixelFormat.RGBA_8888,
                2
            )
            decoder = try {
                MediaCodec.createDecoderByType(videoInfo.mime)
            } catch (e: Exception) {
                throw IllegalStateException("Este aparelho não tem um decoder para ${videoInfo.mime}.", e)
            }
            decoder.configure(format, imageReader.surface, null, 0)
            decoder.start()

            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            val intervalUs = intervalMs * 1_000L
            var nextTargetUs = startUs
            var firstSourceTimestampUs: Long? = null
            var lastWrittenTimestampMs = -1
            var inputEnded = false
            var outputEnded = false
            var framesWritten = 0
            var lastUpdateAt = 0L
            var lastProgressTimestampMs = 0L
            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputEnded) {
                checkExportState(isCancelled, deadline)

                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("O decoder não disponibilizou um buffer de entrada.")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || sampleTimeUs < 0L || sampleTimeUs >= endUs) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, maxOf(0L, sampleTimeUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        val outputWidth = outputFormat.integerOrDefault(MediaFormat.KEY_WIDTH, videoInfo.width)
                        val outputHeight = outputFormat.integerOrDefault(MediaFormat.KEY_HEIGHT, videoInfo.height)
                        require(outputWidth == videoInfo.width && outputHeight == videoInfo.height) {
                            "O decoder alterou as dimensões de saída durante a exportação."
                        }
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val sourceTimeUs = bufferInfo.presentationTimeUs
                        val hasImage = bufferInfo.size > 0 &&
                            sourceTimeUs >= startUs && sourceTimeUs < endUs && sourceTimeUs >= nextTargetUs
                        val isEndOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, hasImage)

                        if (hasImage) {
                            onProgress(
                                AnimatedExportProgress(
                                    stage = AnimatedExportStage.DECODING,
                                    attempt = attempt,
                                    totalAttempts = attemptCount,
                                    frame = framesWritten,
                                    totalFrames = targetFrameCount,
                                    sourceTimeMs = ((sourceTimeUs - startUs) / 1_000L).coerceAtLeast(0L),
                                    frameRate = fps,
                                    quality = quality,
                                    elapsedMs = SystemClock.elapsedRealtime() - exportStartedAt
                                )
                            )
                            val image = awaitImage(imageReader, isCancelled, deadline)
                            val decoded = image.use(::imageToBitmap)
                            val oriented = rotate(decoded, videoInfo.rotation)
                            if (oriented !== decoded) decoded.recycle()
                            val normalized = render(oriented, crop, texts)
                            if (!oriented.isRecycled) oriented.recycle()

                            val firstTimestampUs = firstSourceTimestampUs
                            val relativeTimestampMs = if (firstTimestampUs == null) 0L
                                else ((sourceTimeUs - firstTimestampUs) / 1_000L).coerceAtLeast(0L)
                            val shouldEncode = lastWrittenTimestampMs < 0 ||
                                relativeTimestampMs - lastWrittenTimestampMs >= MIN_FRAME_DURATION_MS
                            if (shouldEncode) {
                                val encoderTimestampMs = relativeTimestampMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                val added = try {
                                    AnimatedWebpNative.addFrame(handle, normalized, encoderTimestampMs)
                                } finally {
                                    normalized.recycle()
                                }
                                if (!added) throw IllegalStateException("O encoder recusou o quadro ${framesWritten + 1} do vídeo.")
                                if (firstTimestampUs == null) firstSourceTimestampUs = sourceTimeUs
                                lastWrittenTimestampMs = encoderTimestampMs
                                framesWritten++
                                lastProgressTimestampMs = encoderTimestampMs.toLong()
                                lastUpdateAt = reportFrameProgress(
                                    onProgress, lastUpdateAt, framesWritten, targetFrameCount,
                                    (sourceTimeUs - startUs) / 1_000L, fps, quality, attempt,
                                    attemptCount, exportStartedAt, AnimatedExportStage.ENCODING
                                )
                            } else {
                                normalized.recycle()
                            }
                            nextTargetUs = sourceTimeUs + intervalUs
                        } else if (bufferInfo.size > 0 && sourceTimeUs in startUs until endUs) {
                            reportFrameProgress(
                                onProgress, lastUpdateAt, framesWritten, targetFrameCount,
                                (sourceTimeUs - startUs) / 1_000L, fps, quality, attempt,
                                attemptCount, exportStartedAt, AnimatedExportStage.DECODING
                            ).also { lastUpdateAt = it }
                        }
                        if (isEndOfStream) outputEnded = true
                    }
                }
                yield()
            }

            if (framesWritten < 2) {
                throw IllegalArgumentException("O decoder não encontrou pelo menos dois quadros no trecho selecionado.")
            }
            checkExportState(isCancelled, deadline)
            val actualEnd = maxOf(duration.toLong(), lastProgressTimestampMs + MIN_FRAME_DURATION_MS)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return AnimatedWebpNative.finish(handle, output.absolutePath, actualEnd) &&
                output.isFile && output.length() > 0L
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                codec.release()
            }
            imageReader?.close()
            extractor.release()
        }
    }

    private suspend fun awaitImage(
        imageReader: ImageReader,
        isCancelled: () -> Boolean,
        deadline: Long
    ): Image {
        while (true) {
            checkExportState(isCancelled, deadline)
            imageReader.acquireNextImage()?.let { return it }
            delay(5L)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        require(image.format == PixelFormat.RGBA_8888) {
            "O decoder retornou pixels em formato ${image.format}; RGBA_8888 era esperado."
        }
        val plane = image.planes.firstOrNull() ?: throw IllegalStateException("O decoder retornou um quadro vazio.")
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        require(pixelStride >= 4 && rowStride >= image.width * pixelStride) {
            "O decoder retornou strides RGBA inválidos (pixel=$pixelStride, linha=$rowStride)."
        }
        val pixels = ByteArray(image.width * image.height * 4)
        val buffer = plane.buffer.duplicate()
        val rowBytes = image.width * 4
        for (row in 0 until image.height) {
            val sourceRow = row * rowStride
            val targetRow = row * rowBytes
            if (pixelStride == 4) {
                buffer.position(sourceRow)
                buffer.get(pixels, targetRow, rowBytes)
            } else {
                for (column in 0 until image.width) {
                    val sourcePixel = sourceRow + column * pixelStride
                    val targetPixel = targetRow + column * 4
                    pixels[targetPixel] = buffer.get(sourcePixel)
                    pixels[targetPixel + 1] = buffer.get(sourcePixel + 1)
                    pixels[targetPixel + 2] = buffer.get(sourcePixel + 2)
                    pixels[targetPixel + 3] = buffer.get(sourcePixel + 3)
                }
            }
        }
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        return bitmap
    }

    private fun rotate(bitmap: Bitmap, rotation: Int): Bitmap {
        val normalizedDegrees = ((rotation % 360) + 360) % 360
        if (normalizedDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalizedDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun reportFrameProgress(
        onProgress: (AnimatedExportProgress) -> Unit,
        previousUpdateAt: Long,
        frame: Int,
        totalFrames: Int,
        sourceTimeMs: Long,
        fps: Int,
        quality: Int,
        attempt: Int,
        attemptCount: Int,
        exportStartedAt: Long,
        stage: AnimatedExportStage
    ): Long {
        val now = SystemClock.elapsedRealtime()
        if (frame == 0 || frame % PROGRESS_FRAME_STEP == 0 || now - previousUpdateAt >= PROGRESS_INTERVAL_MS) {
            onProgress(
                AnimatedExportProgress(
                    stage = stage,
                    attempt = attempt,
                    totalAttempts = attemptCount,
                    frame = frame,
                    totalFrames = totalFrames,
                    sourceTimeMs = sourceTimeMs,
                    frameRate = fps,
                    quality = quality,
                    elapsedMs = now - exportStartedAt
                )
            )
            return now
        }
        return previousUpdateAt
    }

    private suspend fun checkExportState(isCancelled: () -> Boolean, deadline: Long) {
        currentCoroutineContext().ensureActive()
        if (isCancelled()) throw CancellationException("Exportação cancelada pelo usuário.")
        if (SystemClock.elapsedRealtime() >= deadline) {
            throw AnimatedExportTimeoutException("A exportação excedeu 3 minutos. Encurte o trecho e tente novamente.")
        }
    }

    private fun MediaFormat.integerOrDefault(key: String, defaultValue: Int): Int =
        if (containsKey(key)) getInteger(key) else defaultValue

    private const val CODEC_TIMEOUT_US = 10_000L
    private const val MIN_FRAME_DURATION_MS = 8
    private const val PROGRESS_FRAME_STEP = 3
    private const val PROGRESS_INTERVAL_MS = 250L
    private const val MAX_VIDEO_PIXELS = 16L * 1024L * 1024L

    private fun render(source: Bitmap, crop: VideoCropState, texts: List<TextData>): Bitmap {
        val frame = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.save()
        canvas.rotate(crop.rotation, 256f, 256f)
        val scale = max(512f / source.width, 512f / source.height) * crop.scale
        val width = source.width * scale
        val height = source.height * scale
        val left = (512f - width) / 2f + crop.offset.x
        val top = (512f - height) / 2f + crop.offset.y
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
        canvas.restore()
        texts.forEach { text ->
            canvas.save()
            canvas.translate(256f + text.offset.x, 256f + text.offset.y)
            canvas.rotate(text.rotation)
            canvas.scale(text.scale, text.scale)
            val typeface = when (text.fontIndex) {
                1 -> android.graphics.Typeface.SERIF
                2 -> android.graphics.Typeface.MONOSPACE
                3 -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
                else -> android.graphics.Typeface.DEFAULT_BOLD
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 32f
                textAlign = Paint.Align.CENTER
                this.typeface = typeface
                color = text.color.toArgb()
            }
            if (text.strokeWidth > 0f) {
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = text.strokeWidth
                textPaint.color = text.strokeColor.toArgb()
                canvas.drawText(text.text, 0f, 11f, textPaint)
                textPaint.style = Paint.Style.FILL
                textPaint.color = text.color.toArgb()
            }
            canvas.drawText(text.text, 0f, 11f, textPaint)
            canvas.restore()
        }
        return frame
    }

    internal class GifSource(context: Context, uri: Uri) : AutoCloseable {
        private val data = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_GIF_INPUT_BYTES) { "O GIF excede o limite de leitura de 50 MiB." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("Não foi possível ler o GIF selecionado.")
        private val header = GifHeaderParser().setData(data).parseHeader().also {
            require(it.status == GifDecoder.STATUS_OK && it.width in 1..4096 && it.height in 1..4096 &&
                it.width.toLong() * it.height <= MAX_GIF_PIXELS && it.numFrames in 2..5000) {
                "O GIF está inválido ou excede as dimensões/quadros suportados."
            }
        }
        private val provider = object : GifDecoder.BitmapProvider {
            override fun obtain(width: Int, height: Int, config: Bitmap.Config) = Bitmap.createBitmap(width, height, config)
            override fun release(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
            override fun obtainByteArray(size: Int) = ByteArray(size)
            override fun release(bytes: ByteArray) = Unit
            override fun obtainIntArray(size: Int) = IntArray(size)
            override fun release(array: IntArray) = Unit
        }
        private val sample = Integer.highestOneBit(max(1, max(header.width, header.height) / 1024))
        private val decoder = StandardGifDecoder(provider, header, ByteBuffer.wrap(data), sample)
        private val cumulative = LongArray(header.numFrames + 1)
        val frameRate: Float
        val durationMs: Long get() = cumulative.last()
        private var cursor = -1
        private var frameStart = 0L
        private var current: Bitmap? = null

        init {
            for (i in 0 until header.numFrames) cumulative[i + 1] = cumulative[i] + decoder.getDelay(i).coerceAtLeast(0)
            require(cumulative.last() > 0) { "O GIF não possui duração válida." }
            frameRate = (header.numFrames * 1000f / cumulative.last()).coerceIn(8f, 125f)
        }

        fun frameAt(timeMs: Long): Bitmap {
            val normalized = (timeMs % cumulative.last()).coerceAtLeast(0)
            while (cursor + 1 < header.numFrames && normalized >= cumulative[cursor + 1]) {
                current?.recycle()
                decoder.advance()
                current = decoder.nextFrame ?: throw IllegalArgumentException("Não foi possível decodificar um quadro do GIF.")
                cursor++
            }
            if (current == null) {
                decoder.advance()
                current = decoder.nextFrame ?: throw IllegalArgumentException("Não foi possível decodificar o GIF.")
                cursor = 0
            }
            return current!!.copy(Bitmap.Config.ARGB_8888, false)
        }

        override fun close() {
            current?.recycle()
            decoder.clear()
        }

        private companion object {
            const val MAX_GIF_INPUT_BYTES = 50L * 1024L * 1024L
            const val MAX_GIF_PIXELS = 16L * 1024L * 1024L
        }
    }
}
