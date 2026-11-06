package com.example.wppsticker.ui.videoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.example.wppsticker.ui.editor.TextData
import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class AnimatedExportProfile { FLUID, BALANCED, SHARP }

internal data class AnimatedExportResult(val file: File, val frameRate: Int, val quality: Int) {
    val sizeBytes: Long get() = file.length()
}

internal object AnimatedStickerExporter {
    const val MAX_BYTES = 512_000L
    const val MAX_DURATION_MS = 10_000L

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
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean
    ): AnimatedExportResult {
        val start = trimStartMs.coerceAtLeast(0L)
        val end = trimEndMs.coerceAtLeast(start + 8L).coerceAtMost(start + MAX_DURATION_MS)
        require(end - start >= 16L) { "Selecione um trecho de pelo menos 16 ms." }

        val gif = if (isGif) GifSource(context, source) else null
        val retriever = if (!isGif) MediaMetadataRetriever().apply { setDataSource(context, source) } else null
        try {
            val sourceFps = if (gif != null) gif.frameRate else {
                val metadataFps = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
                (metadataFps ?: 30f).coerceIn(8f, 125f)
            }
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
                    if (isCancelled()) throw CancellationException("Exportação cancelada")
                    onProgress("Testando ${fps} fps · qualidade $quality (${index + 1}/${attempts.size})")
                    val candidate = File(outputDirectory, "animated_candidate_${System.nanoTime()}.webp")
                    tempFiles += candidate
                    val ok = encodeCandidate(
                        retriever, gif, start, end, fps, quality, crop, texts, candidate, isCancelled
                    )
                    if (ok && candidate.length() <= MAX_BYTES &&
                        AnimatedWebpNative.isValidAnimatedWebp(candidate.absolutePath)) {
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
            retriever?.release()
            gif?.close()
        }
    }

    private fun encodeCandidate(
        retriever: MediaMetadataRetriever?, gif: GifSource?, start: Long, end: Long,
        fps: Int, quality: Int, crop: VideoCropState, texts: List<TextData>, output: File,
        isCancelled: () -> Boolean
    ): Boolean {
        val interval = max(8, 1000 / fps)
        val duration = (end - start).toInt()
        val frameCount = ceil(duration / interval.toDouble()).toInt().coerceAtLeast(2)
        var handle = AnimatedWebpNative.create(512, 512, quality.toFloat())
        if (handle == 0L) return false
        var added = 0
        try {
            for (index in 0 until frameCount) {
                if (isCancelled()) throw CancellationException("Exportação cancelada")
                val timestamp = index * interval
                if (timestamp >= duration) break
                val sourceBitmap = if (gif != null) gif.frameAt(start + timestamp) else {
                    retriever?.getFrameAtTime((start + timestamp) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                } ?: continue
                val normalized = render(sourceBitmap, crop, texts)
                if (sourceBitmap !== normalized) sourceBitmap.recycle()
                val ok = try { AnimatedWebpNative.addFrame(handle, normalized, timestamp) } finally { normalized.recycle() }
                if (!ok) return false
                added++
            }
            if (added < 2) return false
            val ok = AnimatedWebpNative.finish(handle, output.absolutePath, duration)
            handle = 0
            return ok && output.isFile && output.length() > 0L
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AnimatedWebp", "Candidate encoding failed at $fps fps / q$quality", e)
            return false
        } finally {
            if (handle != 0L) AnimatedWebpNative.abort(handle)
        }
    }

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
