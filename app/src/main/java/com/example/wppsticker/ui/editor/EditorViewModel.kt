package com.example.wppsticker.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.example.wppsticker.util.ImageHelper
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

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val imageUri: StateFlow<Uri?> = MutableStateFlow(Uri.parse(checkNotNull(savedStateHandle["imageUri"])))

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _rotation = MutableStateFlow(0f)
    val rotation: StateFlow<Float> = _rotation.asStateFlow()

    private val _offset = MutableStateFlow(Offset.Zero)
    val offset: StateFlow<Offset> = _offset.asStateFlow()

    private val _isCropMode = MutableStateFlow(false)
    val isCropMode: StateFlow<Boolean> = _isCropMode.asStateFlow()

    private val _cropRect = MutableStateFlow(Rect(100f, 100f, 500f, 500f))
    val cropRect: StateFlow<Rect> = _cropRect.asStateFlow()

    private val _texts = MutableStateFlow<List<TextData>>(emptyList())
    val texts: StateFlow<List<TextData>> = _texts.asStateFlow()

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()

    private val _showTextDialog = MutableStateFlow(false)
    val showTextDialog: StateFlow<Boolean> = _showTextDialog.asStateFlow()

    private val _navigateToSave = MutableStateFlow<String?>(null)
    val navigateToSave: StateFlow<String?> = _navigateToSave.asStateFlow()

    fun onScaleChanged(scale: Float) { _scale.update { it * scale } }
    fun onRotationChanged(rotation: Float) { _rotation.update { it + rotation } }
    fun onOffsetChanged(offset: Offset) { _offset.update { it + offset } }
    fun toggleCropMode() { _isCropMode.update { !it } }
    fun updateCropRect(newRect: Rect) { _cropRect.value = newRect }

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
                    it.copy(
                        offset = it.offset + (offset ?: Offset.Zero),
                        scale = it.scale * (scale ?: 1f),
                        rotation = it.rotation + (rotation ?: 0f)
                    )
                } else { it }
            }
        }
    }

    fun onSaveAndContinue() = viewModelScope.launch {
        _isBusy.value = true
        val finalBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val request = ImageRequest.Builder(context).data(imageUri.value).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        val originalBitmap = result.drawable?.toBitmap() ?: return@launch

        finalBitmap.applyCanvas {
            val paint = Paint().apply { isAntiAlias = true }
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 32f * context.resources.displayMetrics.density
                textAlign = Paint.Align.CENTER
            }
            val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            drawPaint(clearPaint)

            if (isCropMode.value) {
                clipRect(cropRect.value.left, cropRect.value.top, cropRect.value.right, cropRect.value.bottom)
            }

            save()
            translate(width / 2f + offset.value.x, height / 2f + offset.value.y)
            rotate(rotation.value)
            scale(scale.value, scale.value)
            drawBitmap(originalBitmap, -originalBitmap.width / 2f, -originalBitmap.height / 2f, paint)
            restore()

            texts.value.forEach { textData ->
                save()
                textPaint.color = textData.color.toArgb()
                translate(width / 2f + textData.offset.x, height / 2f + textData.offset.y)
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
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
        }
        return file
    }
}
