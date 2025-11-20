package com.example.wppsticker.ui.stickerpack

import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.data.local.StickerPackageWithStickers
import com.example.wppsticker.domain.usecase.DeleteStickerUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackageWithStickersUseCase
import com.example.wppsticker.domain.usecase.UpdateStickerPackageUseCase
import com.example.wppsticker.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackageViewModel @Inject constructor(
    private val getStickerPackageWithStickersUseCase: GetStickerPackageWithStickersUseCase,
    private val deleteStickerUseCase: DeleteStickerUseCase,
    private val updateStickerPackageUseCase: UpdateStickerPackageUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val packageId: Int = checkNotNull(savedStateHandle["packageId"])

    private val _stickerPackage = MutableStateFlow<UiState<StickerPackageWithStickers>>(UiState.Loading)
    val stickerPackage: StateFlow<UiState<StickerPackageWithStickers>> = _stickerPackage.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        getStickerPackageDetails()
    }

    private fun getStickerPackageDetails() {
        getStickerPackageWithStickersUseCase(packageId).onEach { packageWithStickers ->
            // Even if stickers are empty, we want to show the package details
            // so user can edit them or add stickers.
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
            // incrementPackageVersion() call removed
            _uiEvents.emit("Package updated successfully")
        }
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
