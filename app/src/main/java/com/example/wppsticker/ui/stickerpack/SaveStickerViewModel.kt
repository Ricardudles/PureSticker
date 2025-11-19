package com.example.wppsticker.ui.stickerpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.domain.usecase.AddStickerUseCase
import com.example.wppsticker.domain.usecase.CreateStickerPackageUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackagesUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackageWithStickersUseCase
import com.example.wppsticker.domain.usecase.UpdateStickerPackageUseCase
import com.example.wppsticker.util.ImageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    private val getStickerPackageWithStickersUseCase: GetStickerPackageWithStickersUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val stickerUri: Uri = Uri.parse(checkNotNull(savedStateHandle["stickerUri"]))

    private val _stickerPackages = MutableStateFlow<List<StickerPackage>>(emptyList())
    val stickerPackages = _stickerPackages.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    private val _saveFinished = MutableStateFlow(false)
    val saveFinished = _saveFinished.asStateFlow()

    // For validation errors (Toast/Snackbar)
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        loadPackages()
    }

    private fun loadPackages() {
        getStickerPackagesUseCase().onEach { 
            _stickerPackages.value = it
        }.launchIn(viewModelScope)
    }

    fun createNewPackage(
        name: String, 
        author: String,
        email: String,
        website: String,
        privacyPolicy: String,
        licenseAgreement: String,
        onPackageCreated: (StickerPackage) -> Unit
    ) = viewModelScope.launch {
        
        // --- LIMIT CHECK ---
        if (_stickerPackages.value.size >= 10) {
            _uiEvents.emit("You have reached the limit of 10 Sticker Packs.")
            return@launch
        }
        
        // --- VALIDATIONS ---
        if (name.isBlank()) {
            _uiEvents.emit("Package Name is required.")
            return@launch
        }
        if (name.length > 128) {
            _uiEvents.emit("Package Name is too long (max 128 chars).")
            return@launch
        }
        if (author.isBlank()) {
            _uiEvents.emit("Author is required.")
            return@launch
        }
        if (author.length > 128) {
             _uiEvents.emit("Author name is too long (max 128 chars).")
             return@launch
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiEvents.emit("Invalid Email address.")
            return@launch
        }
        if (website.isNotEmpty() && !isValidUrl(website)) {
            _uiEvents.emit("Website must start with http:// or https://")
            return@launch
        }
        if (privacyPolicy.isNotEmpty() && !isValidUrl(privacyPolicy)) {
            _uiEvents.emit("Privacy Policy must start with http:// or https://")
            return@launch
        }
        
        Log.d(TAG, "SaveStickerVM: Creating new package: $name by $author")
        _isBusy.value = true
        
        val newPackage = StickerPackage(
            name = name, 
            author = author, 
            trayImageFile = "",
            publisherEmail = email,
            publisherWebsite = website,
            privacyPolicyWebsite = privacyPolicy,
            licenseAgreementWebsite = licenseAgreement
        )
        
        val newId = createStickerPackageUseCase(newPackage)
        val createdPackage = newPackage.copy(id = newId.toInt())
        onPackageCreated(createdPackage)
        Log.d(TAG, "SaveStickerVM: New package created.")
        _isBusy.value = false
    }

    fun saveSticker(stickerPackage: StickerPackage, emojis: String) = viewModelScope.launch {
        // --- LIMIT CHECK ---
        val currentPackDetails = getStickerPackageWithStickersUseCase(stickerPackage.id).first()
        if (currentPackDetails.stickers.size >= 30) {
             _uiEvents.emit("This pack has reached the limit of 30 stickers.")
             return@launch
        }

        // --- VALIDATION ---
        val cleanedEmojis = emojis.trim()
        if (cleanedEmojis.isEmpty()) {
            _uiEvents.emit("At least one emoji is required.")
            return@launch
        }
        val emojiList = cleanedEmojis.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (emojiList.size > 3) {
            _uiEvents.emit("Max 3 emojis allowed per sticker.")
            return@launch
        }

        Log.d(TAG, "SaveStickerVM: Saving sticker to package '${stickerPackage.name}'")
        _isBusy.value = true

        withContext(Dispatchers.IO) {
            try {
                val sourcePath = stickerUri.path ?: return@withContext
                val sourceBitmap = BitmapFactory.decodeFile(sourcePath)
                
                if (sourceBitmap == null) {
                    _uiEvents.emit("Error loading sticker image.")
                    _isBusy.value = false
                    return@withContext
                }

                // 1. Process Sticker (512x512, < 100KB)
                val finalFileName = "${UUID.randomUUID()}.webp"
                val destinationFile = File(context.filesDir, finalFileName)
                
                val stickerFile = saveImageWithConstraints(
                    bitmap = sourceBitmap,
                    targetFile = destinationFile,
                    targetWidth = 512,
                    targetHeight = 512,
                    maxSizeKb = 100
                )

                if (stickerFile == null) {
                    _uiEvents.emit("Could not compress sticker under 100KB.")
                    _isBusy.value = false
                    return@withContext
                }

                Log.d(TAG, "SaveStickerVM: Sticker saved: ${stickerFile.name} Size: ${stickerFile.length() / 1024}KB")
                
                // Delete temp file from editor if needed
                File(sourcePath).delete()

                val sticker = Sticker(
                    packageId = stickerPackage.id, 
                    imageFile = stickerFile.name, 
                    emojis = emojiList,
                    width = 512, 
                    height = 512,
                    sizeInKb = stickerFile.length() / 1024
                )
                addStickerUseCase(sticker)

                // Increment version
                val currentVersion = stickerPackage.imageDataVersion.toIntOrNull() ?: 1
                val newVersion = (currentVersion + 1).toString()
                var updatedPackage = stickerPackage.copy(imageDataVersion = newVersion)

                // 2. Process Tray Icon if needed (96x96, < 50KB)
                if (updatedPackage.trayImageFile.isEmpty()) {
                    Log.d(TAG, "SaveStickerVM: Setting new tray icon.")
                    val trayFileName = "${UUID.randomUUID()}_tray.webp"
                    val trayFileTarget = File(context.filesDir, trayFileName)

                    val trayFile = saveImageWithConstraints(
                        bitmap = sourceBitmap, // Use original bitmap to resize down directly
                        targetFile = trayFileTarget,
                        targetWidth = 96,
                        targetHeight = 96,
                        maxSizeKb = 50
                    )
                    
                    if (trayFile != null) {
                        updatedPackage = updatedPackage.copy(trayImageFile = trayFile.name)
                    } else {
                        Log.e(TAG, "Failed to generate tray icon under 50KB")
                    }
                }
                
                updateStickerPackageUseCase(updatedPackage)
                _saveFinished.value = true

            } catch (e: Exception) {
                Log.e(TAG, "Error saving sticker", e)
                _uiEvents.emit("Error saving sticker: ${e.message}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Resizes and compresses a bitmap to meet strict WhatsApp constraints.
     * Returns the file if successful, null otherwise.
     */
    private fun saveImageWithConstraints(
        bitmap: Bitmap, 
        targetFile: File, 
        targetWidth: Int, 
        targetHeight: Int, 
        maxSizeKb: Int
    ): File? {
        try {
            // 1. Resize exact dimensions
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            
            // 2. Compress loop (WebP)
            var quality = 90
            var stream: ByteArrayOutputStream
            var byteArray: ByteArray

            do {
                stream = ByteArrayOutputStream()
                // Using WEBP_LOSSY as it's standard for stickers (supports transparency)
                // API 30+ has WEBP_LOSSY, below is WEBP. 
                // Using simply WEBP for compatibility (it defaults to lossy with quality param).
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    resizedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
                } else {
                    @Suppress("DEPRECATION")
                    resizedBitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
                }
                
                byteArray = stream.toByteArray()
                val sizeKb = byteArray.size / 1024
                Log.d(TAG, "Compression check: ${targetWidth}x${targetHeight} Q:$quality Size:${sizeKb}KB (Max: $maxSizeKb)")

                if (sizeKb <= maxSizeKb) {
                    FileOutputStream(targetFile).use { out ->
                        out.write(byteArray)
                    }
                    return targetFile
                }

                quality -= 10
            } while (quality > 0)

            return null // Could not compress enough
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveImageWithConstraints", e)
            return null
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}
