package com.example.wppsticker.ui.stickerpack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.R
import com.example.wppsticker.data.local.StickerPackageWithStickers
import com.example.wppsticker.domain.repository.StickerRepository
import com.example.wppsticker.domain.usecase.DeleteStickerUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackageWithStickersUseCase
import com.example.wppsticker.domain.usecase.UpdateStickerPackageUseCase
import com.example.wppsticker.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PackageViewModel"

@HiltViewModel
class PackageViewModel @Inject constructor(
    private val getStickerPackageWithStickersUseCase: GetStickerPackageWithStickersUseCase,
    private val deleteStickerUseCase: DeleteStickerUseCase,
    private val updateStickerPackageUseCase: UpdateStickerPackageUseCase,
    private val stickerRepository: StickerRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val packageId: Int = checkNotNull(savedStateHandle["packageId"])

    private val _stickerPackage = MutableStateFlow<UiState<StickerPackageWithStickers>>(UiState.Loading)
    val stickerPackage: StateFlow<UiState<StickerPackageWithStickers>> = _stickerPackage.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    private val _sendIntent = MutableStateFlow<Intent?>(null)
    val sendIntent: StateFlow<Intent?> = _sendIntent.asStateFlow()

    init {
        getStickerPackageDetails()
    }

    private fun getStickerPackageDetails() {
        getStickerPackageWithStickersUseCase(packageId).onEach { packageWithStickers ->
            _stickerPackage.value = UiState.Success(packageWithStickers)
        }.launchIn(viewModelScope)
    }

    fun deleteSticker(stickerId: Int) {
        viewModelScope.launch {
            deleteStickerUseCase(stickerId)
            incrementPackageVersion()
        }
    }
    
    fun deleteStickers(stickerIds: List<Int>) {
        viewModelScope.launch {
            stickerIds.forEach { id ->
                deleteStickerUseCase(id)
            }
            incrementPackageVersion()
        }
    }

    fun updatePackageDetails(
        name: String, 
        author: String, 
        email: String, 
        website: String, 
        privacyPolicy: String, 
        license: String
    ) = viewModelScope.launch {
        
        // --- VALIDATIONS ---
        if (name.isBlank()) {
            _uiEvents.emit(context.getString(R.string.pkg_name_required_error))
            return@launch
        }
        if (name.length > 128) {
            _uiEvents.emit(context.getString(R.string.pkg_name_too_long_error))
            return@launch
        }
        if (author.isBlank()) {
            _uiEvents.emit(context.getString(R.string.author_required_error))
            return@launch
        }
        if (author.length > 128) {
             _uiEvents.emit(context.getString(R.string.author_too_long_error))
             return@launch
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiEvents.emit(context.getString(R.string.invalid_email_error))
            return@launch
        }
        if (website.isNotEmpty() && !isValidUrl(website)) {
            _uiEvents.emit(context.getString(R.string.invalid_url_error))
            return@launch
        }
        if (privacyPolicy.isNotEmpty() && !isValidUrl(privacyPolicy)) {
            _uiEvents.emit(context.getString(R.string.invalid_url_error))
            return@launch
        }
    
        val currentState = _stickerPackage.value
        if (currentState is UiState.Success) {
            val currentPackage = currentState.data.stickerPackage
            
            // Increment version inline to avoid race condition with UI state flow
            val currentVersion = currentPackage.imageDataVersion.toIntOrNull() ?: 1
            val newVersion = (currentVersion + 1).toString()

            val updatedPackage = currentPackage.copy(
                name = name,
                author = author,
                publisherEmail = email,
                publisherWebsite = website,
                privacyPolicyWebsite = privacyPolicy,
                licenseAgreementWebsite = license,
                imageDataVersion = newVersion
            )
            
            updateStickerPackageUseCase(updatedPackage)
            _uiEvents.emit(context.getString(R.string.package_updated_success))
        }
    }

    fun sendStickerPack() = viewModelScope.launch {
        val state = _stickerPackage.value
        if (state !is UiState.Success) return@launch
        
        val stickerPackage = state.data
        
        // Check limits
        if (stickerPackage.stickers.size < 3) {
            _uiEvents.emit(context.getString(R.string.min_stickers_error))
            return@launch
        }
        if (stickerPackage.stickers.size > 30) {
            _uiEvents.emit(context.getString(R.string.max_stickers_error))
            return@launch
        }

        if (stickerPackage.stickerPackage.trayImageFile.isEmpty()) {
             _uiEvents.emit(context.getString(R.string.tray_icon_error))
            return@launch
        }

        val identifier = stickerPackage.stickerPackage.identifier 

        // Check whitelist
        val isAdded = stickerRepository.isStickerPackageWhitelisted(identifier)
        if (isAdded) {
            _uiEvents.emit(context.getString(R.string.package_already_added))
             return@launch
        }

        val authority = "${context.packageName}.provider"
        val name = stickerPackage.stickerPackage.name
        val action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

        val intent = Intent().apply {
            this.action = action
            putExtra("sticker_pack_id", identifier)
            putExtra("sticker_pack_authority", authority)
            putExtra("sticker_pack_name", name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val uriPrefix = "content://$authority/sticker_packs/$identifier"
            val packUri = Uri.parse(uriPrefix)
            val stickersUri = Uri.parse("$uriPrefix/stickers")
            
            listOf("com.whatsapp", "com.whatsapp.w4b").forEach { pkg ->
                try {
                    context.grantUriPermission(pkg, packUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.grantUriPermission(pkg, stickersUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not grant permission to $pkg")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up permissions", e)
        }
        
        _sendIntent.value = intent
    }

    fun onSendIntentLaunched() {
        _sendIntent.value = null
    }

    private suspend fun incrementPackageVersion() {
        val currentState = _stickerPackage.value
        if (currentState is UiState.Success) {
            val currentPackage = currentState.data.stickerPackage
            val currentVersion = currentPackage.imageDataVersion.toIntOrNull() ?: 1
            val newVersion = (currentVersion + 1).toString()
            updateStickerPackageUseCase(currentPackage.copy(imageDataVersion = newVersion))
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}
