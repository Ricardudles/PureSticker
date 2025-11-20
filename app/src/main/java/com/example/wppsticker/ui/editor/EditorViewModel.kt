package com.example.wppsticker.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlin.math.min

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

    init {
        val uriString = savedStateHandle.get<String>("imageUri")
        if (uriString != null) {
            _imageUri.value = Uri.parse(uriString)
        }
    }

    fun onImageCropped(uri: Uri) {
        _imageUri.value = uri
        // Reset state on new image
        _imageState.value = ImageState()
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

    // Updates the text transformation.
    // offset should be in 512x512 canvas units.
    fun updateSelectedText(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
        _texts.update { texts ->
            texts.map {
                if (it.id == _selectedTextId.value) {
                    it.copy(
                        offset = it.offset + (offset ?: Offset.Zero),
                        scale = it.scale * (scale ?: 1f),
                        rotation = it.rotation + (rotation ?: 0f)
                    )
                } else { it }
            }
        }
    }

    // Updates the background image transformation.
    // offset should be in 512x512 canvas units.
    fun updateImageState(offset: Offset? = null, scale: Float? = null, rotation: Float? = null) {
        _imageState.update { current ->
            current.copy(
                offset = current.offset + (offset ?: Offset.Zero),
                scale = current.scale * (scale ?: 1f),
                rotation = current.rotation + (rotation ?: 0f)
            )
        }
    }

    fun onSaveAndContinue() = viewModelScope.launch {
        _isBusy.value = true
        val currentUri = imageUri.value ?: return@launch

        val request = ImageRequest.Builder(context).data(currentUri).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        val originalBitmap = result.drawable?.toBitmap() ?: return@launch

        // Canvas size
        val canvasWidth = 512
        val canvasHeight = 512
        val finalBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        
        finalBitmap.applyCanvas {
            val paint = Paint().apply { isAntiAlias = true }
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 32f // Base text size in canvas units
                textAlign = Paint.Align.CENTER
            }

            // --- Draw Background Image ---
            // 1. Calculate base scale to FIT the image into 512x512 initially (similar to ContentScale.Fit)
            val baseScale = min(canvasWidth.toFloat() / originalBitmap.width, canvasHeight.toFloat() / originalBitmap.height)
            
            val currentState = _imageState.value

            // Matrix logic:
            // We want to apply: Translate(Center) -> UserTransforms -> Scale(Base) -> Translate(-Center) (Wait, no)
            // The drawing logic on screen usually is: 
            // Render at center. Apply scale/rotation around center.
            // So:
            // 1. Move to center of canvas (256, 256)
            // 2. Apply User Offset
            // 3. Apply User Rotation
            // 4. Apply User Scale
            // 5. Apply Base Scale (to bring image to "fit" size)
            // 6. Draw Bitmap centered at (0,0) [so move by -w/2, -h/2]

            save()
            translate(canvasWidth / 2f, canvasHeight / 2f)
            
            // Apply User Transforms
            translate(currentState.offset.x, currentState.offset.y)
            rotate(currentState.rotation)
            scale(currentState.scale, currentState.scale)

            // Apply Base Scale
            scale(baseScale, baseScale)

            // Draw centered
            translate(-originalBitmap.width / 2f, -originalBitmap.height / 2f)
            drawBitmap(originalBitmap, 0f, 0f, paint)
            restore()

            // --- Draw Texts ---
            texts.value.forEach { textData ->
                save()
                textPaint.color = textData.color.toArgb()
                // Texts are also positioned relative to center in our logic (offset from (0,0))
                // We translate to center first
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
