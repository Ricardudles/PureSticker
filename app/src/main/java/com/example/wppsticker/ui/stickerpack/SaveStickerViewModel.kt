package com.example.wppsticker.ui.stickerpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.R
import com.example.wppsticker.data.StickerEmojiPolicy
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.domain.usecase.AddStickerUseCase
import com.example.wppsticker.domain.usecase.CreateStickerPackageUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackagesUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackageWithStickersUseCase
import com.example.wppsticker.domain.usecase.UpdateStickerPackageUseCase
import com.example.wppsticker.domain.usecase.SaveStickerAndPackageUseCase
import com.example.wppsticker.ui.videoeditor.AnimatedWebpNative
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    private val saveStickerAndPackageUseCase: SaveStickerAndPackageUseCase,
    private val getStickerPackageWithStickersUseCase: GetStickerPackageWithStickersUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // If stickerUri is passed as arg, use it. If not (e.g. when reused in PackageSelectionScreen), it might be null or passed differently.
    val stickerUri: Uri = run {
         val uriString = savedStateHandle.get<String>("stickerUri")
         if (uriString != null) Uri.parse(uriString) else Uri.EMPTY
    }
    
    val packageIdArg: Int = savedStateHandle.get<Int>("packageId") ?: -1
    val isAnimatedArg: Boolean = savedStateHandle.get<Boolean>("isAnimated") ?: false

    private val _stickerPackages = MutableStateFlow<List<StickerPackage>>(emptyList())
    val stickerPackages = _stickerPackages.asStateFlow()
    
    private val _preSelectedPackage = MutableStateFlow<StickerPackage?>(null)
    val preSelectedPackage = _preSelectedPackage.asStateFlow()

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
        isAnimated: Boolean,
        onPackageCreated: (StickerPackage) -> Unit
    ) = viewModelScope.launch {
        
        // --- LIMIT CHECK ---
        if (_stickerPackages.value.size >= 10) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.limit_packs_error)))
            return@launch
        }
        
        // --- VALIDATIONS ---
        if (name.isBlank()) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.pkg_name_required_error)))
            return@launch
        }
        if (name.length > 128) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.pkg_name_too_long_error)))
            return@launch
        }
        if (author.isBlank()) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.author_required_error)))
            return@launch
        }
        if (author.length > 128) {
             _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.author_too_long_error)))
             return@launch
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.invalid_email_error)))
            return@launch
        }
        if (website.isNotEmpty() && !isValidUrl(website)) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.invalid_url_error)))
            return@launch
        }
        if (privacyPolicy.isNotEmpty() && !isValidUrl(privacyPolicy)) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.invalid_url_error)))
            return@launch
        }
        
        Log.d(TAG, "SaveStickerVM: Creating new package: $name by $author")
        _loadingMessage.value = context.getString(R.string.creating_package)
        
        val newPackage = StickerPackage(
            name = name, 
            author = author, 
            trayImageFile = "",
            publisherEmail = email,
            publisherWebsite = website,
            privacyPolicyWebsite = privacyPolicy,
            licenseAgreementWebsite = licenseAgreement,
            animated = isAnimated // Use renamed property
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
             _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.limit_stickers_error)))
             return@launch
        }

        // --- VALIDATION ---
        val cleanedEmojis = emojis.trim()
        // Decode emojis if they are URL encoded (Phase 3.4 fix)
        val decodedEmojis = try {
            java.net.URLDecoder.decode(cleanedEmojis, java.nio.charset.StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            cleanedEmojis
        }
        
        val selectedEmojiList = decodedEmojis.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (selectedEmojiList.size > StickerEmojiPolicy.MAX_EMOJIS_PER_STICKER) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.max_emojis_error)))
            return@launch
        }
        val emojiList = StickerEmojiPolicy.withDefaultIfEmpty(selectedEmojiList)

        // Log.d(TAG, "SaveStickerVM: Saving sticker to package '${stickerPackage.name}' (Animated: ${stickerPackage.animated})")
        _loadingMessage.value = context.getString(R.string.analyzing_image)

        withContext(Dispatchers.IO) {
            var importedSourceFile: File? = null
            try {
                val sourceFile = if (stickerUri.scheme == "file") File(stickerUri.path!!) else {
                    val imported = File(context.cacheDir, "selected-${UUID.randomUUID()}")
                    context.contentResolver.openInputStream(stickerUri)?.use { input ->
                        imported.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalArgumentException("Não foi possível ler a figurinha selecionada.")
                    imported
                }
                if (stickerUri.scheme != "file") importedSourceFile = sourceFile
                if (!sourceFile.exists()) {
                     _events.emit(SaveStickerEvent.ShowToast("Source sticker file not found"))
                     return@withContext
                }
                val fileHash = calculateHash(sourceFile)

                // --- DUPLICATE CHECK ---
                if (!force) {
                    val isDuplicate = currentPackDetails.stickers.any { it.imageFileHash == fileHash }
                    if (isDuplicate) {
                        _events.emit(SaveStickerEvent.ShowDuplicateDialog {
                            // User confirmed, run the save again with force = true
                            saveSticker(stickerPackage, decodedEmojis, force = true)
                        })
                        _loadingMessage.value = null 
                        return@withContext
                    }
                }
                
                // Explicit cast to avoid ambiguity if any
                val pkg = stickerPackage as com.example.wppsticker.data.local.StickerPackage
                val sourceIsAnimatedWebp = AnimatedWebpNative.isValidAnimatedWebp(sourceFile.absolutePath)
                if (sourceIsAnimatedWebp && !pkg.animated) {
                    _events.emit(SaveStickerEvent.ShowToast("GIFs e vídeos só podem ser salvos em pacotes animados."))
                    return@withContext
                }
                if (pkg.animated) { // Use renamed property
                    saveAnimatedSticker(stickerPackage, emojiList, sourceFile, fileHash)
                } else {
                    saveStaticSticker(stickerPackage, emojiList, sourceFile, fileHash)
                }
            } catch (e: CancellationException) {
                throw e
            } catch(e: Exception) {
                Log.e(TAG, "Error saving sticker", e)
                _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.error_saving, e.message)))
            } finally {
                importedSourceFile?.delete()
                _loadingMessage.value = null
            }
        }
    }

    private suspend fun saveAnimatedSticker(stickerPackage: StickerPackage, emojis: List<String>, sourceFile: File, fileHash: String) {
        _loadingMessage.value = context.getString(R.string.processing_sticker)
        var generatedWebp: File? = null
        var destinationTemp: File? = null
        var destination: File? = null
        var firstFrame: Bitmap? = null
        var updatedPackageForCleanup: StickerPackage? = null
        var committed = false
        try {
            val animatedSource = if (AnimatedWebpNative.isValidAnimatedWebp(sourceFile.absolutePath)) {
                sourceFile
            } else {
                val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                    ?: throw IllegalArgumentException("O arquivo não é um WebP animado válido nem uma imagem estática compatível.")
                firstFrame = bitmap
                val normalized = normalizeToStickerCanvas(bitmap)
                generatedWebp = File(context.cacheDir, "static-animated-${UUID.randomUUID()}.webp")
                val muxed = AnimatedWebpNative.encodeStaticTwoFrames(normalized, generatedWebp!!.absolutePath)
                if (normalized !== bitmap) normalized.recycle()
                if (!muxed) throw IllegalStateException("Não foi possível montar dois quadros WebP para o pacote animado.")
                generatedWebp!!
            }
            val sizeBytes = animatedSource.length()
            if (sizeBytes > 512_000L) {
                _events.emit(SaveStickerEvent.ShowToast("A figurinha excede 500 KiB (limite de 512.000 bytes)."))
                return
            }
            if (!AnimatedWebpNative.isValidAnimatedWebp(animatedSource.absolutePath)) {
                throw IllegalArgumentException("A validação WebP falhou: dimensão, quadros ou duração fora dos limites.")
            }
            firstFrame = firstFrame ?: AnimatedWebpNative.decodeFirstFrame(animatedSource.absolutePath)
                ?: decodeFirstFrame(animatedSource)
                ?: throw IllegalArgumentException("Não foi possível decodificar o primeiro quadro para o ícone do pacote.")

            val updatedPackage = buildUpdatedPackage(stickerPackage, firstFrame!!)
            updatedPackageForCleanup = updatedPackage
            if (updatedPackage.trayImageFile.isBlank()) {
                throw IllegalStateException("Não foi possível criar o ícone do pacote.")
            }
            val finalFileName = "${UUID.randomUUID()}.webp"
            destination = File(context.filesDir, finalFileName)
            destinationTemp = File(context.filesDir, "$finalFileName.tmp")
            animatedSource.copyTo(destinationTemp!!, overwrite = true)
            if (!AnimatedWebpNative.isValidAnimatedWebp(destinationTemp!!.absolutePath) ||
                destinationTemp!!.length() != sizeBytes || !destinationTemp!!.renameTo(destination!!)) {
                throw IllegalStateException("Não foi possível validar e instalar o arquivo WebP final.")
            }
            val sticker = Sticker(
                packageId = stickerPackage.id,
                imageFile = finalFileName,
                imageFileHash = fileHash,
                emojis = emojis,
                width = 512,
                height = 512,
                sizeInKb = (sizeBytes + 1023L) / 1024L
            )
            saveStickerAndPackageUseCase(sticker, updatedPackage)
            committed = true
            if (sourceFile.parentFile == File(context.cacheDir, "animated-sticker-export")) sourceFile.delete()
            _saveFinished.value = true
        } catch (e: CancellationException) {
            destination?.delete()
            throw e
        } catch (e: Exception) {
            destination?.delete()
            _events.emit(SaveStickerEvent.ShowToast(e.localizedMessage ?: "Falha ao validar WebP animado."))
        } finally {
            destinationTemp?.delete()
            generatedWebp?.delete()
            if (!committed && stickerPackage.trayImageFile.isBlank()) {
                updatedPackageForCleanup?.trayImageFile?.takeIf(String::isNotBlank)
                    ?.let { File(context.filesDir, it).delete() }
            }
            firstFrame?.recycle()
        }
    }
    
    private suspend fun saveStaticSticker(stickerPackage: StickerPackage, emojis: List<String>, sourceFile: File, fileHash: String) {
        _loadingMessage.value = context.getString(R.string.processing_sticker)
        val sourceBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        
        if (sourceBitmap == null) {
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.error_loading_image)))
            return
        }

        // 1. Process Sticker (512x512, < 100KB)
        _loadingMessage.value = context.getString(R.string.optimizing_sticker)
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
            _events.emit(SaveStickerEvent.ShowToast(context.getString(R.string.error_compression)))
            return
        }

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

        updateTrayIconAndVersion(stickerPackage, sourceBitmap)
        _saveFinished.value = true
    }
    
    private suspend fun updateTrayIconAndVersion(stickerPackage: StickerPackage, sourceBitmap: Bitmap?) {
        updateStickerPackageUseCase(buildUpdatedPackage(stickerPackage, sourceBitmap))
    }

    private suspend fun buildUpdatedPackage(stickerPackage: StickerPackage, sourceBitmap: Bitmap?): StickerPackage {
        // Increment version
        val currentVersion = stickerPackage.imageDataVersion.toIntOrNull() ?: 1
        val newVersion = (currentVersion + 1).toString()
        var updatedPackage = stickerPackage.copy(imageDataVersion = newVersion)

        // Process Tray Icon if needed (96x96, < 50KB)
        if (updatedPackage.trayImageFile.isEmpty() && sourceBitmap != null) {
            Log.d(TAG, "SaveStickerVM: Setting new tray icon.")
            _loadingMessage.value = context.getString(R.string.generating_tray)
            val trayFileName = "${UUID.randomUUID()}_tray.webp"
            val trayFileTarget = File(context.filesDir, trayFileName)

            val trayFile = saveImageWithConstraints(
                bitmap = sourceBitmap, 
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
        
        return updatedPackage
    }

    private fun normalizeToStickerCanvas(bitmap: Bitmap): Bitmap {
        if (bitmap.width == 512 && bitmap.height == 512) return bitmap
        val canvasBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(canvasBitmap)
        val scale = minOf(512f / bitmap.width, 512f / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (512f - width) / 2f
        val top = (512f - height) / 2f
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + width, top + height),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
        return canvasBitmap
    }

    private fun decodeFirstFrame(file: File): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // This ensures we only decode the first frame, which is more efficient.
                    // decoder.isAnimated = false // Property removed/unavailable
                }
            } else {
                // Fallback for older APIs, might not be as reliable for all animated formats.
                BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode first frame", e)
            null
        }
    }
    
    private fun saveImageWithConstraints(
        bitmap: Bitmap, 
        targetFile: File, 
        targetWidth: Int, 
        targetHeight: Int, 
        maxSizeKb: Int
    ): File? {
        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            var quality = 90
            var stream: ByteArrayOutputStream
            var byteArray: ByteArray

            do {
                stream = ByteArrayOutputStream()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    resizedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
                } else {
                    @Suppress("DEPRECATION")
                    resizedBitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
                }
                
                byteArray = stream.toByteArray()
                val sizeKb = byteArray.size / 1024

                if (sizeKb <= maxSizeKb) {
                    FileOutputStream(targetFile).use { out ->
                        out.write(byteArray)
                    }
                    return targetFile
                }
                quality -= 10
            } while (quality > 0)

            return null
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
