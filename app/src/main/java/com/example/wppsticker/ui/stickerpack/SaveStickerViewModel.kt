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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

private const val TAG = "StickerAppDebug"

sealed class SaveStickerEvent {
    data class ShowDuplicateDialog(val onConfirm: () -> Unit) : SaveStickerEvent()
    data class ShowToast(val message: String) : SaveStickerEvent()
}

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
    val packageIdArg: Int = savedStateHandle.get<Int>("packageId") ?: -1

    private val _stickerPackages = MutableStateFlow<List<StickerPackage>>(emptyList())
    val stickerPackages = _stickerPackages.asStateFlow()
    
    private val _preSelectedPackage = MutableStateFlow<StickerPackage?>(null)
    val preSelectedPackage = _preSelectedPackage.asStateFlow()

    // Changed from isBusy to loadingMessage for better feedback
    private val _loadingMessage = MutableStateFlow<String?>(null)
    val loadingMessage = _loadingMessage.asStateFlow()

    private val _saveFinished = MutableStateFlow(false)
    val saveFinished = _saveFinished.asStateFlow()

    private val _events = MutableSharedFlow<SaveStickerEvent>()
    val events = _events.asSharedFlow()

    init {
        loadPackages()
    }

    private fun loadPackages() {
        getStickerPackagesUseCase().onEach { packages ->
            _stickerPackages.value = packages
            if (packageIdArg != -1) {
                _preSelectedPackage.value = packages.find { it.id == packageIdArg }
            }
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
            _events.emit(SaveStickerEvent.ShowToast("You have reached the limit of 10 Sticker Packs."))
            return@launch
        }
        
        // --- VALIDATIONS ---
        if (name.isBlank()) {
            _events.emit(SaveStickerEvent.ShowToast("Package Name is required."))
            return@launch
        }
        if (name.length > 128) {
            _events.emit(SaveStickerEvent.ShowToast("Package Name is too long (max 128 chars)."))
            return@launch
        }
        if (author.isBlank()) {
            _events.emit(SaveStickerEvent.ShowToast("Author is required."))
            return@launch
        }
        if (author.length > 128) {
             _events.emit(SaveStickerEvent.ShowToast("Author name is too long (max 128 chars)."))
             return@launch
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _events.emit(SaveStickerEvent.ShowToast("Invalid Email address."))
            return@launch
        }
        if (website.isNotEmpty() && !isValidUrl(website)) {
            _events.emit(SaveStickerEvent.ShowToast("Website must start with http:// or https://"))
            return@launch
        }
        if (privacyPolicy.isNotEmpty() && !isValidUrl(privacyPolicy)) {
            _events.emit(SaveStickerEvent.ShowToast("Privacy Policy must start with http:// or https://"))
            return@launch
        }
        
        Log.d(TAG, "SaveStickerVM: Creating new package: $name by $author")
        _loadingMessage.value = "Creating package..."
        
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
        _loadingMessage.value = null
    }

    fun saveSticker(stickerPackage: StickerPackage, emojis: String, force: Boolean = false): Job = viewModelScope.launch {
        // --- LIMIT CHECK ---
        val currentPackDetails = getStickerPackageWithStickersUseCase(stickerPackage.id).first()
        if (currentPackDetails.stickers.size >= 30) {
             _events.emit(SaveStickerEvent.ShowToast("This pack has reached the limit of 30 stickers."))
             return@launch
        }

        // --- VALIDATION ---
        val cleanedEmojis = emojis.trim()
        if (cleanedEmojis.isEmpty()) {
            _events.emit(SaveStickerEvent.ShowToast("At least one emoji is required."))
            return@launch
        }
        val emojiList = cleanedEmojis.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (emojiList.size > 3) {
            _events.emit(SaveStickerEvent.ShowToast("Max 3 emojis allowed per sticker."))
            return@launch
        }

        Log.d(TAG, "SaveStickerVM: Saving sticker to package '${stickerPackage.name}'")
        _loadingMessage.value = "Analyzing image..."

        withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(stickerUri.path!!)
                val fileHash = calculateHash(sourceFile)

                // --- DUPLICATE CHECK ---
                if (!force) {
                    val isDuplicate = currentPackDetails.stickers.any { it.imageFileHash == fileHash }
                    if (isDuplicate) {
                        _events.emit(SaveStickerEvent.ShowDuplicateDialog {
                            // User confirmed, run the save again with force = true
                            saveSticker(stickerPackage, emojis, force = true)
                        })
                        _loadingMessage.value = null // Stop loading while dialog is shown
                        return@withContext
                    }
                }
                
                proceedWithSave(stickerPackage, emojiList, sourceFile, fileHash)

            } catch(e: Exception) {
                Log.e(TAG, "Error saving sticker", e)
                _events.emit(SaveStickerEvent.ShowToast("Error saving sticker: ${e.message}"))
            } finally {
                _loadingMessage.value = null
            }
        }
    }
    
    private suspend fun proceedWithSave(stickerPackage: StickerPackage, emojis: List<String>, sourceFile: File, fileHash: String) {
        _loadingMessage.value = "Processing sticker..."
        val sourceBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        
        if (sourceBitmap == null) {
            _events.emit(SaveStickerEvent.ShowToast("Error loading sticker image."))
            return
        }

        // 1. Process Sticker (512x512, < 100KB)
        _loadingMessage.value = "Optimizing sticker..."
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
            _events.emit(SaveStickerEvent.ShowToast("Could not compress sticker under 100KB."))
            return
        }

        Log.d(TAG, "SaveStickerVM: Sticker saved: ${stickerFile.name} Size: ${stickerFile.length() / 1024}KB")
        
        // Delete temp file from editor if needed
        // sourceFile.delete() // Let editor handle its own cache, or delete here if passed by uri

        val sticker = Sticker(
            packageId = stickerPackage.id, 
            imageFile = stickerFile.name, 
            imageFileHash = fileHash,
            emojis = emojis,
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
            _loadingMessage.value = "Generating tray icon..."
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
    
    @OptIn(ExperimentalStdlibApi::class)
    private fun calculateHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead = fis.read(buffer)
                while(bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = fis.read(buffer)
                }
            }
            digest.digest().toHexString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}
