package com.example.wppsticker.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.util.BackgroundRemover
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Stack
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _imageState = MutableStateFlow(ImageState())
    val imageState: StateFlow<ImageState> = _imageState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _texts = MutableStateFlow<List<TextData>>(emptyList())
    val texts: StateFlow<List<TextData>> = _texts.asStateFlow()

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()

    private val _showTextDialog = MutableStateFlow(false)
    val showTextDialog: StateFlow<Boolean> = _showTextDialog.asStateFlow()

    private val _navigateToSave = MutableStateFlow<String?>(null)
    val navigateToSave: StateFlow<String?> = _navigateToSave.asStateFlow()
    
    private val _isSnapEnabled = MutableStateFlow(true)
    val isSnapEnabled: StateFlow<Boolean> = _isSnapEnabled.asStateFlow()
    
    private val _snapStrength = MutableStateFlow(3) // Default Level 3
    val snapStrength: StateFlow<Int> = _snapStrength.asStateFlow()

    // Undo/Redo Stacks
    private val undoStack = Stack<EditorState>()
    private val redoStack = Stack<EditorState>()
    
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    val packageIdArg: Int = savedStateHandle.get<Int>("packageId") ?: -1

    // Raw state to prevent "stuck" snap behavior
    private var rawOffset = Offset.Zero
    private var rawScale = 1f
    private var rawRotation = 0f
    
    // Image dimensions for edge snapping
    private var imageWidth = 0
    private var imageHeight = 0

    init {
        val uriString = savedStateHandle.get<String>("stickerUri")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            _imageUri.value = uri
            loadImageDimensions(uri)
        }
    }

    // --- Undo/Redo Logic ---
    
    private fun pushToUndoStack() {
        val currentState = EditorState(
            imageState = _imageState.value,
            texts = _texts.value,
            imageUri = _imageUri.value // Save current URI
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
            // Save current state to redo stack first
            val currentState = EditorState(
                imageState = _imageState.value,
                texts = _texts.value,
                imageUri = _imageUri.value
            )
            redoStack.push(currentState)
            
            // Restore from undo stack
            val previousState = undoStack.pop()
            restoreState(previousState)
            updateUndoRedoState()
        }
    }
    
    fun redo() {
        if (redoStack.isNotEmpty()) {
            // Save current state to undo stack
            val currentState = EditorState(
                imageState = _imageState.value,
                texts = _texts.value,
                imageUri = _imageUri.value
            )
            undoStack.push(currentState)
            
            // Restore from redo stack
            val nextState = redoStack.pop()
            restoreState(nextState)
            updateUndoRedoState()
        }
    }
    
    private fun restoreState(state: EditorState) {
        // Restore URI if it changed (e.g. background removal undo)
        if (state.imageUri != null && state.imageUri != _imageUri.value) {
            _imageUri.value = state.imageUri
            loadImageDimensions(state.imageUri)
        }

        _imageState.value = state.imageState
        _texts.value = state.texts
        
        // Also restore raw values to prevent snap glitches when modifying again
        rawOffset = state.imageState.offset
        rawScale = state.imageState.scale
        rawRotation = state.imageState.rotation
    }
    
    fun toggleSnap(enabled: Boolean) {
        _isSnapEnabled.value = enabled
    }
    
    fun setSnapStrength(level: Int) {
        _snapStrength.value = level.coerceIn(1, 5)
    }

    fun onImageCropped(uri: Uri) {
        // Pushes state BEFORE the change
        pushToUndoStack()
        
        _imageUri.value = uri
        // Reset state on new image (or keep? usually keep if it's just bg removal)
        // If it's a crop, usually we reset or adjust. 
        // For BG removal, we probably want to keep scale/rotation if possible, 
        // but reset helps avoid visual glitches if dimensions changed drastically.
        _imageState.value = ImageState()
        rawOffset = Offset.Zero
        rawScale = 1f
        rawRotation = 0f
        
        loadImageDimensions(uri)
    }
    
    private fun loadImageDimensions(uri: Uri) {
        viewModelScope.launch {
            val request = ImageRequest.Builder(context).data(uri).build()
            val result = context.imageLoader.execute(request)
            result.drawable?.let { 
                 imageWidth = it.intrinsicWidth
                 imageHeight = it.intrinsicHeight
            }
        }
    }

    fun addText(text: String) {
        pushToUndoStack()
        val newText = TextData(text = text)
        _texts.update { it + newText }
        _selectedTextId.value = newText.id
        _showTextDialog.value = false
    }

    fun onTextSelected(id: String?) { _selectedTextId.value = id }
    fun showTextDialog(show: Boolean) { _showTextDialog.value = show }

    fun updateSelectedTextColor(color: androidx.compose.ui.graphics.Color) {
        pushToUndoStack()
        _texts.update { texts ->
            texts.map {
                if (it.id == _selectedTextId.value) {
                    it.copy(color = color)
                } else { it }
            }
        }
    }
    
    fun updateSelectedTextFont(fontIndex: Int) {
        pushToUndoStack()
        _texts.update { texts ->
            texts.map {
                if (it.id == _selectedTextId.value) {
                    it.copy(fontIndex = fontIndex)
                } else { it }
            }
        }
    }

    // Helper to detect start of a gesture for undo purposes
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

    fun updateSelectedText(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
        // We rely on onGestureStart to push state before this stream of updates
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
        _texts.update { texts ->
            texts.map {
                if (it.id == _selectedTextId.value) {
                    it.copy(scale = scale)
                } else { it }
            }
        }
    }

    // Updates the background image transformation.
    fun updateImageState(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
        // We rely on onGestureStart to push state before this stream of updates
        
        // 1. Accumulate Raw Values (Unsnapped)
        rawOffset += (offset ?: Offset.Zero)
        if (scale != null) rawScale *= scale
        if (rotation != null) rawRotation += rotation
        
        // 2. Calculate Candidate Values for Display
        var finalRotation = rawRotation
        var finalOffset = rawOffset
        
        if (_isSnapEnabled.value) {
             // Determine strength parameters
             val (rotationTol, positionTol) = when(_snapStrength.value) {
                 1 -> 2f to 5f   // Weakest
                 2 -> 5f to 15f  // Weak
                 3 -> 10f to 30f // Medium (Default - Current)
                 4 -> 15f to 50f // Strong
                 5 -> 25f to 80f // Strongest
                 else -> 10f to 30f
             }
        
             // --- Rotation Snap ---
             // Round to nearest 90 degrees
             val nearest90 = (rawRotation / 90).roundToInt() * 90f
             
             if (abs(rawRotation - nearest90) < rotationTol) {
                 finalRotation = nearest90
             }
             
             // --- Edge Snap ---
             if (finalRotation % 90f == 0f && imageWidth > 0 && imageHeight > 0) {
                  val canvasSize = 512f
                  val baseScale = min(canvasSize / imageWidth, canvasSize / imageHeight)
                  val effectiveScale = baseScale * rawScale
                  
                  // If rotated 90 or 270, width and height are swapped in canvas space
                  val rotationStep = (finalRotation.toInt() / 90)
                  val isSwapped = abs(rotationStep) % 2 == 1 
                  
                  val currentWidth = if (isSwapped) imageHeight * effectiveScale else imageWidth * effectiveScale
                  val currentHeight = if (isSwapped) imageWidth * effectiveScale else imageHeight * effectiveScale
                  
                  val centerX = 256f + rawOffset.x
                  val centerY = 256f + rawOffset.y
                  
                  val left = centerX - currentWidth / 2f
                  val right = centerX + currentWidth / 2f
                  val top = centerY - currentHeight / 2f
                  val bottom = centerY + currentHeight / 2f
                  
                  var snappedX = rawOffset.x
                  var snappedY = rawOffset.y
                  
                  // Center Snap (Weak)
                  if (abs(rawOffset.x) < positionTol) snappedX = 0f
                  if (abs(rawOffset.y) < positionTol) snappedY = 0f
                  
                  // Edge Snap (Stronger - Overrides Center if detected)
                  // Left Edge -> 0
                  if (abs(left - 0f) < positionTol) {
                      snappedX = (currentWidth / 2f) - 256f
                  }
                  // Right Edge -> 512
                  else if (abs(right - 512f) < positionTol) {
                      snappedX = (512f - currentWidth / 2f) - 256f
                  }
                  
                  // Top Edge -> 0
                  if (abs(top - 0f) < positionTol) {
                      snappedY = (currentHeight / 2f) - 256f
                  }
                  // Bottom Edge -> 512
                  else if (abs(bottom - 512f) < positionTol) {
                      snappedY = (512f - currentHeight / 2f) - 256f
                  }
                  
                  finalOffset = Offset(snappedX, snappedY)
             } else {
                  // Simple Center Snap if rotated at weird angles
                  if (abs(rawOffset.x) < positionTol) finalOffset = finalOffset.copy(x = 0f)
                  if (abs(rawOffset.y) < positionTol) finalOffset = finalOffset.copy(y = 0f)
             }
        }
        
        _imageState.update {
            it.copy(offset = finalOffset, scale = rawScale, rotation = finalRotation)
        }
    }
    
    fun removeBackground() = viewModelScope.launch {
        // REMOVED pushToUndoStack() here because onImageCropped does it
        val uri = imageUri.value ?: return@launch
        _isBusy.value = true
        try {
             val request = ImageRequest.Builder(context)
                 .data(uri)
                 .allowHardware(false)
                 .build()
             val result = context.imageLoader.execute(request)
             val bitmap = result.drawable?.toBitmap()
             
             if (bitmap != null) {
                 val processed = BackgroundRemover.removeBackground(bitmap)
                 
                 // Save to temp file (PNG to preserve alpha)
                 val file = File(context.cacheDir, "bg_removed_${UUID.randomUUID()}.png")
                 FileOutputStream(file).use { out ->
                     processed.compress(Bitmap.CompressFormat.PNG, 100, out)
                 }
                 
                 // Update State
                 onImageCropped(Uri.fromFile(file))
             }
        } catch (e: Exception) {
             e.printStackTrace()
        } finally {
             _isBusy.value = false
        }
    }
    
    fun onNavigatedToSave() {
        _navigateToSave.value = null
    }

    fun onSaveAndContinue() = viewModelScope.launch {
        _isBusy.value = true
        val currentUri = imageUri.value ?: return@launch

        val request = ImageRequest.Builder(context).data(currentUri).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        val originalBitmap = result.drawable?.toBitmap() ?: return@launch

        val canvasWidth = 512
        val canvasHeight = 512
        val finalBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        
        finalBitmap.applyCanvas {
            val paint = Paint().apply { isAntiAlias = true }
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 32f // Fixed base size matching the UI calculation
                textAlign = Paint.Align.CENTER
            }

            val baseScale = min(canvasWidth.toFloat() / originalBitmap.width, canvasHeight.toFloat() / originalBitmap.height)
            val currentState = _imageState.value

            save()
            translate(canvasWidth / 2f, canvasHeight / 2f)
            translate(currentState.offset.x, currentState.offset.y)
            rotate(currentState.rotation)
            scale(currentState.scale, currentState.scale)
            scale(baseScale, baseScale)
            translate(-originalBitmap.width / 2f, -originalBitmap.height / 2f)
            drawBitmap(originalBitmap, 0f, 0f, paint)
            restore()

            texts.value.forEach { textData ->
                save()
                textPaint.color = textData.color.toArgb()
                
                // Ensure font matching matches StickerTextDisplay in UI
                textPaint.typeface = when(textData.fontIndex) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    3 -> Typeface.create("cursive", Typeface.ITALIC)
                    4 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    else -> Typeface.SANS_SERIF 
                }

                translate(canvasWidth / 2f, canvasHeight / 2f)
                translate(textData.offset.x, textData.offset.y)
                rotate(textData.rotation)
                scale(textData.scale, textData.scale)
                // Apply a small vertical offset to center text vertically based on baseline
                val fontMetrics = textPaint.fontMetrics
                val verticalOffset = (fontMetrics.descent - fontMetrics.ascent) / 2 - fontMetrics.descent
                drawText(textData.text, 0f, verticalOffset, textPaint)
                restore()
            }
        }
        
        val file = saveBitmapToTempFile(finalBitmap)
        val encodedUri = URLEncoder.encode(Uri.fromFile(file).toString(), StandardCharsets.UTF_8.toString())
        
        // Here we set packageId to -1 if not provided or not relevant for initial save
        // However, since the user asked for pre-selection logic, we should pass packageIdArg if available.
        var route = "${Screen.SaveSticker.name}/$encodedUri"
        if (packageIdArg != -1) {
            route += "?packageId=$packageIdArg"
        } else {
            route += "?packageId=-1"
        }
        
        _navigateToSave.value = route
        _isBusy.value = false
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): File {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.webp")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
        }
        return file
    }
}
