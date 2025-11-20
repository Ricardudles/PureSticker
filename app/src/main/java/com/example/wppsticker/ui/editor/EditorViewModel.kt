package com.example.wppsticker.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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

    // Raw state to prevent "stuck" snap behavior
    private var rawOffset = Offset.Zero
    private var rawScale = 1f
    private var rawRotation = 0f
    
    // Image dimensions for edge snapping
    private var imageWidth = 0
    private var imageHeight = 0

    init {
        val uriString = savedStateHandle.get<String>("imageUri")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            _imageUri.value = uri
            loadImageDimensions(uri)
        }
    }
    
    fun toggleSnap(enabled: Boolean) {
        _isSnapEnabled.value = enabled
    }
    
    fun setSnapStrength(level: Int) {
        _snapStrength.value = level.coerceIn(1, 5)
    }

    fun onImageCropped(uri: Uri) {
        _imageUri.value = uri
        // Reset state on new image
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
        val newText = TextData(text = text)
        _texts.update { it + newText }
        _selectedTextId.value = newText.id
        _showTextDialog.value = false
    }

    fun onTextSelected(id: String?) { _selectedTextId.value = id }
    fun showTextDialog(show: Boolean) { _showTextDialog.value = show }

    fun updateSelectedTextColor(color: androidx.compose.ui.graphics.Color) {
        _texts.update { texts ->
            texts.map {
                if (it.id == _selectedTextId.value) {
                    it.copy(color = color)
                } else { it }
            }
        }
    }

    fun updateSelectedText(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
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
             // Only apply edge snap if rotation is cleanly snapped (0, 90, 180...)
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
                textSize = 32f 
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
                translate(canvasWidth / 2f, canvasHeight / 2f)
                translate(textData.offset.x, textData.offset.y)
                rotate(textData.rotation)
                scale(textData.scale, textData.scale)
                drawText(textData.text, 0f, 0f, textPaint)
                restore()
            }
        }
        
        val file = saveBitmapToTempFile(finalBitmap)
        _navigateToSave.value = Uri.fromFile(file).toString()
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
