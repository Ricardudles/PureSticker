package com.example.wppsticker.ui.videoeditor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.wppsticker.ui.editor.TextData
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Stack
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
@OptIn(UnstableApi::class)
class VideoEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // --- VIDEO STATE ---
    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = _videoUri.asStateFlow()

    private val _isGif = MutableStateFlow(false)
    val isGif: StateFlow<Boolean> = _isGif.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _videoDuration = MutableStateFlow(0L)
    val videoDuration: StateFlow<Long> = _videoDuration.asStateFlow()

    // Trim Range (Start ms, End ms)
    private val _trimRange = MutableStateFlow(0L..0L)
    val trimRange: StateFlow<LongRange> = _trimRange.asStateFlow()

    // Thumbnails for Timeline
    private val _thumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    val thumbnails: StateFlow<List<Bitmap>> = _thumbnails.asStateFlow()

    // --- CROP STATE (Zoom/Pan) ---
    private val _cropState = MutableStateFlow(VideoCropState())
    val cropState: StateFlow<VideoCropState> = _cropState.asStateFlow()

    // --- TEXT STATE (Shared Logic) ---
    private val _texts = MutableStateFlow<List<TextData>>(emptyList())
    val texts: StateFlow<List<TextData>> = _texts.asStateFlow()

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()
    
    private val _showTextDialog = MutableStateFlow(false)
    val showTextDialog: StateFlow<Boolean> = _showTextDialog.asStateFlow()

    // --- UI CONTROL ---
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    
    private val _processingMessage = MutableStateFlow<String?>(null)
    val processingMessage: StateFlow<String?> = _processingMessage.asStateFlow()

    private val _exportProgress = MutableStateFlow(AnimatedExportProgress())
    val exportProgress: StateFlow<AnimatedExportProgress> = _exportProgress.asStateFlow()

    private val _exportFailure = MutableStateFlow<AnimatedExportFailure?>(null)
    val exportFailure: StateFlow<AnimatedExportFailure?> = _exportFailure.asStateFlow()

    private val _navigateToSave = MutableStateFlow<String?>(null)
    val navigateToSave: StateFlow<String?> = _navigateToSave.asStateFlow()

    // --- GRID STATE ---
    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    // --- UNDO/REDO ---
    private val undoStack = Stack<VideoEditorState>()
    private val redoStack = Stack<VideoEditorState>()
    
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _exportProfile = MutableStateFlow(AnimatedExportProfile.BALANCED)
    val exportProfile: StateFlow<AnimatedExportProfile> = _exportProfile.asStateFlow()
    private var exportJob: Job? = null
    private var isTrimGestureInProgress = false
    private var wasPlayingBeforeTrim = false
    private var hasInitializedVideoTrim = false

    // --- PLAYER ---
    var player: ExoPlayer? = null
        private set
        
    val packageIdArg: Int = savedStateHandle.get<Int>("packageId") ?: -1

    init {
        val uriString = savedStateHandle.get<String>("stickerUri") 
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            _videoUri.value = uri
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val gif = mimeType.equals("image/gif", ignoreCase = true) ||
                uri.lastPathSegment?.substringBefore('?')?.endsWith(".gif", ignoreCase = true) == true
            _isGif.value = gif
            if (gif) initializeGif(uri) else {
                initializePlayer(uri)
                generateThumbnails(uri)
            }
        }
    }

    // --- UNDO/REDO LOGIC ---
    private fun pushToUndoStack() {
        val currentState = VideoEditorState(
            cropState = _cropState.value,
            texts = _texts.value,
            trimRange = _trimRange.value
        )
        undoStack.push(currentState)
        redoStack.clear()
        updateUndoRedoState()
    }
    
    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
    
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = VideoEditorState(
                cropState = _cropState.value,
                texts = _texts.value,
                trimRange = _trimRange.value
            )
            redoStack.push(currentState)
            
            val previousState = undoStack.pop()
            restoreState(previousState)
            updateUndoRedoState()
        }
    }
    
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = VideoEditorState(
                cropState = _cropState.value,
                texts = _texts.value,
                trimRange = _trimRange.value
            )
            undoStack.push(currentState)
            
            val nextState = redoStack.pop()
            restoreState(nextState)
            updateUndoRedoState()
        }
    }
    
    private fun restoreState(state: VideoEditorState) {
        _cropState.value = state.cropState
        _texts.value = state.texts
        _trimRange.value = state.trimRange
        // Update player position if trim changed?
        player?.seekTo(state.trimRange.first)
    }

    @OptIn(UnstableApi::class) 
    private fun initializePlayer(uri: Uri) {
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE // Loop video for editing
            prepare()
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val duration = duration.coerceAtLeast(0)
                        _videoDuration.value = duration

                        // Apply the default once; seeks can return the player to STATE_READY.
                        if (duration > 0L && !hasInitializedVideoTrim) {
                            val end = minOf(duration, AnimatedStickerExporter.MAX_DURATION_MS)
                            _trimRange.value = 0L..end
                            hasInitializedVideoTrim = true
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
        }
        
        // Position Poller
        viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    if (p.isPlaying) {
                        val current = p.currentPosition
                        _currentPosition.value = current
                        
                        // Enforce Trim Loop
                        val end = _trimRange.value.last
                        val start = _trimRange.value.first
                        if (current >= end && end > 0) {
                            p.seekTo(start)
                        }
                    }
                }
                delay(50) // Update every 50ms
            }
        }
    }

    private fun generateThumbnails(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            
            if (durationMs > 0) {
                val numThumbnails = 8
                val interval = durationMs / numThumbnails
                val bitmaps = mutableListOf<Bitmap>()
                
                for (i in 0 until numThumbnails) {
                    val timeUs = (i * interval) * 1000
                    // Use CLOSEST_SYNC for performance, frame doesn't need to be exact for thumbnail strip
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bitmap != null) {
                        // Scale down to save memory (e.g., height 100px)
                        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val targetHeight = 100
                        val targetWidth = (targetHeight * aspectRatio).toInt()
                        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
                        if (scaled != bitmap) bitmap.recycle()
                        bitmaps.add(scaled)
                    }
                }
                _thumbnails.value = bitmaps
            }
        } catch (e: Exception) {
            Log.e("VideoEditor", "Error generating thumbnails", e)
        } finally {
            retriever.release()
        }
    }

    private fun initializeGif(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            AnimatedStickerExporter.GifSource(context, uri).use { gif ->
                _videoDuration.value = gif.durationMs
                _trimRange.value = 0L..minOf(gif.durationMs, AnimatedStickerExporter.MAX_DURATION_MS)
                val thumbs = (0..7).mapNotNull { index ->
                    val time = gif.durationMs * index / 8
                    runCatching { gif.frameAt(time) }.getOrNull()?.let { bitmap ->
                        Bitmap.createScaledBitmap(bitmap, 96, 96, true).also { bitmap.recycle() }
                    }
                }
                _thumbnails.value = thumbs
            }
        } catch (e: Exception) {
            Log.e("VideoEditor", "Could not inspect GIF", e)
            _processingMessage.value = "Não foi possível ler este GIF: ${e.localizedMessage}"
        }
    }

    // --- GESTURE HELPERS ---
    private var isGestureInProgress = false

    fun onGestureStart() {
        if (!isGestureInProgress) {
            pushToUndoStack()
            isGestureInProgress = true
        }
    }

    fun onGestureEnd() {
        isGestureInProgress = false
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val duration = _videoDuration.value
        if (duration < 16L) return
        val safeStart = startMs.coerceIn(0L, duration - 16L)
        val maximumEnd = minOf(duration, safeStart + AnimatedStickerExporter.MAX_DURATION_MS)
        val safeEnd = endMs.coerceIn(safeStart + 16L, maximumEnd)
        if (safeEnd <= safeStart) return

        if (!isTrimGestureInProgress) {
            pushToUndoStack()
            isTrimGestureInProgress = true
            wasPlayingBeforeTrim = player?.isPlaying == true
            player?.pause()
        }
        _trimRange.value = safeStart..safeEnd
        // Seeking on every pointer update stalls the player and fights the slider gesture.
    }

    fun setTrimDuration(durationMs: Long) {
        val sourceDuration = _videoDuration.value
        if (sourceDuration < 16L) return
        val selectedDuration = durationMs.coerceIn(16L, minOf(sourceDuration, AnimatedStickerExporter.MAX_DURATION_MS))
        val start = _trimRange.value.first.coerceAtMost(sourceDuration - selectedDuration)
        pushToUndoStack()
        isTrimGestureInProgress = false
        _trimRange.value = start..(start + selectedDuration)
        player?.seekTo(start)
        _currentPosition.value = start
    }

    fun onTrimChangeFinished() {
        if (!isTrimGestureInProgress) return
        isTrimGestureInProgress = false
        val start = _trimRange.value.first
        player?.seekTo(start)
        _currentPosition.value = start
        if (wasPlayingBeforeTrim) player?.play()
        wasPlayingBeforeTrim = false
    }

    fun setExportProfile(profile: AnimatedExportProfile) {
        _exportProfile.value = profile
    }
    
    // Captures state BEFORE modification
    fun onTrimChangeStarted() {
        pushToUndoStack()
    }

    // --- CROP LOGIC ---
    fun updateCropState(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
        // Relies on onGestureStart called before this
        _cropState.update { current ->
            var newScale = current.scale * (scale ?: 1f)
            newScale = newScale.coerceIn(1f, 5f) // Zoom limit (Video must fill or zoom in)
            
            val newOffset = current.offset + (offset ?: Offset.Zero)
            
            current.copy(offset = newOffset, scale = newScale, rotation = current.rotation + (rotation ?: 0f))
        }
    }

    // --- TEXT LOGIC (Reused) ---
    fun addText(text: String) {
        pushToUndoStack()
        val newText = TextData(text = text)
        _texts.update { it + newText }
        _selectedTextId.value = newText.id
        _showTextDialog.value = false
    }
    
    fun deleteSelectedText() {
        val id = _selectedTextId.value ?: return
        pushToUndoStack()
        _texts.update { it.filter { text -> text.id != id } }
        _selectedTextId.value = null
    }

    fun onTextSelected(id: String?) { _selectedTextId.value = id }
    fun showTextDialog(show: Boolean) { _showTextDialog.value = show }

    fun updateSelectedTextColor(color: androidx.compose.ui.graphics.Color) {
        pushToUndoStack()
        _texts.update { texts -> texts.map { if (it.id == _selectedTextId.value) it.copy(color = color) else it } }
    }
    
    fun updateSelectedTextFont(fontIndex: Int) {
        pushToUndoStack()
        _texts.update { texts -> texts.map { if (it.id == _selectedTextId.value) it.copy(fontIndex = fontIndex) else it } }
    }

    fun updateSelectedText(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
        // Relies on onGestureStart called before this
        _texts.update { texts ->
            texts.map {
                if (it.id == _selectedTextId.value) {
                    var newScale = it.scale * (scale ?: 1f)
                    newScale = newScale.coerceIn(0.1f, 10f)
                    it.copy(
                        offset = it.offset + (offset ?: Offset.Zero),
                        scale = newScale,
                        rotation = it.rotation + (rotation ?: 0f)
                    )
                } else { it }
            }
        }
    }
    
    fun setTextScale(scale: Float) {
        // Push state before modification. 
        // Ideally called on drag start.
        _texts.update { texts -> texts.map { if (it.id == _selectedTextId.value) it.copy(scale = scale) else it } }
    }

    // --- GRID LOGIC ---
    fun toggleGrid(show: Boolean) {
        _showGrid.value = show
    }

    // Decode source frames and encode a validated animated WebP with libwebp.
    fun onSave() {
        if (_isBusy.value) return
        exportJob = viewModelScope.launch {
            _isBusy.value = true
            _exportFailure.value = null
            val startedAt = SystemClock.elapsedRealtime()
            _exportProgress.value = AnimatedExportProgress(stage = AnimatedExportStage.PREPARING)
            val uri = _videoUri.value ?: run { _isBusy.value = false; return@launch }
            val exportContext = kotlin.coroutines.coroutineContext
            var result: AnimatedExportResult? = null
            val elapsedTicker = launch {
                while (isActive) {
                    delay(1_000L)
                    _exportProgress.update { it.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt) }
                }
            }
            try {
                result = withTimeout(AnimatedStickerExporter.MAX_EXPORT_TIME_MS) {
                    withContext(Dispatchers.IO) {
                        val outputDirectory = File(context.cacheDir, "animated-sticker-export").apply { mkdirs() }
                        AnimatedStickerExporter.export(
                            context = context,
                            source = uri,
                            isGif = _isGif.value,
                            trimStartMs = _trimRange.value.first,
                            trimEndMs = _trimRange.value.last,
                            crop = _cropState.value,
                            texts = _texts.value,
                            profile = _exportProfile.value,
                            outputDirectory = outputDirectory,
                            onProgress = { progress ->
                                _exportProgress.value = progress.copy(
                                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                                )
                            },
                            isCancelled = { !exportContext.isActive }
                        )
                    }
                }
                val output = result ?: error("A exportação não retornou um arquivo.")
                val encodedUri = URLEncoder.encode(Uri.fromFile(output.file).toString(), StandardCharsets.UTF_8.toString())
                _navigateToSave.value = "${Screen.SaveSticker.name}/$encodedUri?packageId=$packageIdArg&isAnimated=true"
            } catch (e: TimeoutCancellationException) {
                result?.file?.delete()
                publishExportFailure(
                    IllegalStateException(
                        context.getString(R.string.animated_export_timeout_message), e
                    )
                )
            } catch (e: CancellationException) {
                result?.file?.delete()
                throw e
            } catch (e: Exception) {
                result?.file?.delete()
                publishExportFailure(e)
            } finally {
                elapsedTicker.cancel()
                _isBusy.value = false
            }
        }
    }

    fun cancelSave() {
        exportJob?.cancel()
        _isBusy.value = false
        _processingMessage.value = null
    }

    fun dismissExportFailure() {
        _exportFailure.value = null
    }

    private fun publishExportFailure(error: Exception) {
        Log.e("VideoEditor", "Could not export animated WebP", error)
        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        _exportFailure.value = AnimatedExportFailure(
            message = error.localizedMessage ?: context.getString(R.string.animated_export_error_generic),
            debugDetails = if (isDebuggable) Log.getStackTraceString(error) else null
        )
    }

    fun onNavigatedToSave() {
        _navigateToSave.value = null
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        exportJob?.cancel()
    }
}
