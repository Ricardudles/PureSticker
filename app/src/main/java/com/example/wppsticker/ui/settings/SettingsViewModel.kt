package com.example.wppsticker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.domain.repository.BackupRepository
import com.example.wppsticker.domain.usecase.CleanOrphanFilesUseCase
import com.example.wppsticker.domain.usecase.CleanupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val cleanOrphanFilesUseCase: CleanOrphanFilesUseCase
) : ViewModel() {

    private val _cleanupResult = MutableStateFlow<CleanupResult?>(null)
    val cleanupResult = _cleanupResult.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        try {
            backupRepository.exportBackup(uri)
            _uiEvent.send(UiEvent.ShowToast("Backup created successfully!"))
        } catch (e: Exception) {
            _uiEvent.send(UiEvent.ShowToast("Error creating backup: ${e.message}"))
        }
    }

    fun cleanOrphanFiles() = viewModelScope.launch {
        _cleanupResult.value = cleanOrphanFilesUseCase()
    }

    fun onCleanupResultDialogDismissed() {
        _cleanupResult.value = null
    }

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
    }
}
