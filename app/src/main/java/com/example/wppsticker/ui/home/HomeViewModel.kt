package com.example.wppsticker.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.domain.repository.StickerRepository
import com.example.wppsticker.domain.usecase.CreateStickerPackageUseCase
import com.example.wppsticker.domain.usecase.DeleteStickerPackageUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackageWithStickersUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackagesUseCase
import com.example.wppsticker.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StickerAppDebug"

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getStickerPackagesUseCase: GetStickerPackagesUseCase,
    private val deleteStickerPackageUseCase: DeleteStickerPackageUseCase,
    private val getStickerPackageWithStickersUseCase: GetStickerPackageWithStickersUseCase,
    private val createStickerPackageUseCase: CreateStickerPackageUseCase,
    private val stickerRepository: StickerRepository, // Injected for whitelist check
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _stickerPackages = MutableStateFlow<UiState<List<StickerPackage>>>(UiState.Loading)
    val stickerPackages: StateFlow<UiState<List<StickerPackage>>> = _stickerPackages.asStateFlow()

    private val _sendIntent = MutableStateFlow<Intent?>(null)
    val sendIntent: StateFlow<Intent?> = _sendIntent.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        getStickerPackages()
    }

    private fun getStickerPackages() {
        getStickerPackagesUseCase().onEach { packages ->
            _stickerPackages.value = if (packages.isEmpty()) UiState.Empty else UiState.Success(packages)
        }.launchIn(viewModelScope)
    }

    fun deleteStickerPackage(packageId: Int) = viewModelScope.launch {
        deleteStickerPackageUseCase(packageId)
    }

    fun createStickerPackage(name: String, onPackageCreated: (Long) -> Unit) = viewModelScope.launch {
        val trimmedName = if (name.length > 30) name.substring(0, 30) else name
        val newPackage = StickerPackage(name = trimmedName, author = "Me", trayImageFile = "")
        val newPackageId = createStickerPackageUseCase(newPackage)
        onPackageCreated(newPackageId)
    }

    fun sendStickerPack(packageId: Int) = viewModelScope.launch {
        Log.d(TAG, "SendStickerPack: Attempting to send package with id: $packageId")
        val stickerPackage = getStickerPackageWithStickersUseCase(packageId).first()

        // Check limits before sending
        if (stickerPackage.stickers.size < 3) {
            Log.w(TAG, "SendStickerPack: Failed. Not enough stickers (${stickerPackage.stickers.size})")
            _uiEvents.emit("A sticker pack needs at least 3 stickers.")
            return@launch
        }
        if (stickerPackage.stickers.size > 30) {
            Log.w(TAG, "SendStickerPack: Failed. Too many stickers (${stickerPackage.stickers.size})")
            _uiEvents.emit("A sticker pack cannot have more than 30 stickers.")
            return@launch
        }

        if (stickerPackage.stickerPackage.trayImageFile.isEmpty()) {
            Log.w(TAG, "SendStickerPack: Failed. No tray icon.")
            _uiEvents.emit("A sticker pack needs a tray icon.")
            return@launch
        }

        // Size check (Total package size recommendation is vague, but individual stickers must be < 100KB)
        // We already check individual sticker size on SaveStickerViewModel.
        // However, let's double check tray icon existence and valid format if possible (we assume WebP).

        val identifier = stickerPackage.stickerPackage.identifier // Use UUID identifier

        // Check if already whitelisted
        val isAdded = stickerRepository.isStickerPackageWhitelisted(identifier)
        if (isAdded) {
            Log.d(TAG, "SendStickerPack: Package already added to WhatsApp.")
            _uiEvents.emit("Package already added! WhatsApp will update it automatically.")
            return@launch
        }

        val authority = "${context.packageName}.provider"
        val name = stickerPackage.stickerPackage.name

        // Correct Action for WhatsApp Sticker Integration
        val action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

        Log.d(TAG, "SendStickerPack: Building Intent.")
        val intent = Intent().apply {
            this.action = action
            putExtra("sticker_pack_id", identifier)
            putExtra("sticker_pack_authority", authority)
            putExtra("sticker_pack_name", name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Explicitly grant permissions to WhatsApp to read the provider URIs
        try {
            // Updated URIs to match ContentProvider paths that support String identifiers
            val uriPrefix = "content://$authority/sticker_packs/$identifier"
            val packUri = Uri.parse(uriPrefix)
            val stickersUri = Uri.parse("$uriPrefix/stickers")
            
            // Grant to both standard and business just in case
            listOf("com.whatsapp", "com.whatsapp.w4b").forEach { pkg ->
                try {
                    context.grantUriPermission(pkg, packUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.grantUriPermission(pkg, stickersUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    Log.d(TAG, "Granted URI permission to $pkg for $packUri and $stickersUri")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not grant permission to $pkg: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up permissions", e)
        }
        
        Log.d(TAG, "Attempting to launch implicit intent")
        _sendIntent.value = intent
    }

    fun onSendIntentLaunched() {
        Log.d(TAG, "SendStickerPack: Intent launched via Launcher.")
        _sendIntent.value = null
    }
}
