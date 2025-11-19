package com.example.wppsticker.ui.stickerpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.domain.usecase.AddStickerUseCase
import com.example.wppsticker.domain.usecase.CreateStickerPackageUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackagesUseCase
import com.example.wppsticker.domain.usecase.UpdateStickerPackageUseCase
import com.example.wppsticker.util.ImageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

private const val TAG = "StickerAppDebug"

@HiltViewModel
class SaveStickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getStickerPackagesUseCase: GetStickerPackagesUseCase,
    private val createStickerPackageUseCase: CreateStickerPackageUseCase,
    private val addStickerUseCase: AddStickerUseCase,
    private val updateStickerPackageUseCase: UpdateStickerPackageUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val stickerUri: Uri = Uri.parse(checkNotNull(savedStateHandle["stickerUri"]))

    private val _stickerPackages = MutableStateFlow<List<StickerPackage>>(emptyList())
    val stickerPackages = _stickerPackages.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    private val _saveFinished = MutableStateFlow(false)
    val saveFinished = _saveFinished.asStateFlow()

    init {
        loadPackages()
    }

    private fun loadPackages() {
        getStickerPackagesUseCase().onEach { 
            _stickerPackages.value = it
        }.launchIn(viewModelScope)
    }

    fun createNewPackage(name: String, onPackageCreated: (StickerPackage) -> Unit) = viewModelScope.launch {
        Log.d(TAG, "SaveStickerVM: Creating new package with name: $name")
        _isBusy.value = true
        val newPackage = StickerPackage(name = name, author = "Me", trayImageFile = "")
        val newId = createStickerPackageUseCase(newPackage)
        val createdPackage = newPackage.copy(id = newId.toInt())
        onPackageCreated(createdPackage)
        Log.d(TAG, "SaveStickerVM: New package created with id: ${createdPackage.id}")
        _isBusy.value = false
    }

    fun saveSticker(stickerPackage: StickerPackage, emojis: String) = viewModelScope.launch {
        Log.d(TAG, "SaveStickerVM: Saving sticker to package '${stickerPackage.name}' (id: ${stickerPackage.id})")
        _isBusy.value = true
        
        val sourceFile = File(stickerUri.path!!)
        val finalFileName = "${UUID.randomUUID()}.webp"
        val destinationFile = File(context.filesDir, finalFileName)
        sourceFile.copyTo(destinationFile, overwrite = true)
        sourceFile.delete()
        Log.d(TAG, "SaveStickerVM: Sticker file moved to permanent storage: $finalFileName")

        val sticker = Sticker(
            packageId = stickerPackage.id, 
            imageFile = destinationFile.name, 
            emojis = emojis.split(","),
            width = 512, // We know the size from the editor
            height = 512,
            sizeInKb = destinationFile.length() / 1024
        )
        addStickerUseCase(sticker)

        if (stickerPackage.trayImageFile.isEmpty()) {
            Log.d(TAG, "SaveStickerVM: Package has no tray icon. Setting this sticker as the new tray icon.")
            try {
                val bitmap = BitmapFactory.decodeFile(destinationFile.absolutePath)
                val resizedTrayIcon = ImageHelper.resizeBitmap(bitmap, 96, 96)
                // Save tray icon with slightly lower quality to ensure it stays < 50KB
                val trayFile = saveBitmapToFile(resizedTrayIcon, "${UUID.randomUUID()}_tray.webp", quality = 80)
                updateStickerPackageUseCase(stickerPackage.copy(trayImageFile = trayFile.name))
            } catch (e: Exception) {
                Log.e(TAG, "Error creating tray icon", e)
            }
        }

        _saveFinished.value = true
        _isBusy.value = false
        Log.d(TAG, "SaveStickerVM: Save process finished.")
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String, quality: Int): File {
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
        }
        return file
    }
}
