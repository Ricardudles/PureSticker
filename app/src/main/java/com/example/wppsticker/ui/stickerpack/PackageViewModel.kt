package com.example.wppsticker.ui.stickerpack

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.data.local.StickerPackageWithStickers
import com.example.wppsticker.domain.usecase.DeleteStickerUseCase
import com.example.wppsticker.domain.usecase.GetStickerPackageWithStickersUseCase
import com.example.wppsticker.domain.usecase.UpdateStickerPackageUseCase
import com.example.wppsticker.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        getStickerPackageDetails()
    }

    private fun getStickerPackageDetails() {
        getStickerPackageWithStickersUseCase(packageId).onEach { packageWithStickers ->
            if (packageWithStickers.stickers.isEmpty()) {
                _stickerPackage.value = UiState.Empty
            } else {
                _stickerPackage.value = UiState.Success(packageWithStickers)
            }
        }.launchIn(viewModelScope)
    }

    fun deleteSticker(stickerId: Int) {
        viewModelScope.launch {
            deleteStickerUseCase(stickerId)
            
            // Update package version to notify WhatsApp of changes
            val currentState = _stickerPackage.value
            if (currentState is UiState.Success) {
                val currentPackage = currentState.data.stickerPackage
                val currentVersion = currentPackage.imageDataVersion.toIntOrNull() ?: 1
                val newVersion = (currentVersion + 1).toString()
                updateStickerPackageUseCase(currentPackage.copy(imageDataVersion = newVersion))
            }
        }
    }
}
