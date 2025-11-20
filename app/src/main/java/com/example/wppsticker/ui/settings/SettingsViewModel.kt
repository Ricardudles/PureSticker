package com.example.wppsticker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.domain.repository.BackupRepository
import com.example.wppsticker.domain.usecase.CleanOrphanFilesUseCase
import com.example.wppsticker.domain.usecase.CleanupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val cleanOrphanFilesUseCase: CleanOrphanFilesUseCase
) : ViewModel() {

    private val _cleanupResult = MutableStateFlow<CleanupResult?>(null)
    val cleanupResult = _cleanupResult.asStateFlow()

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        try {
            backupRepository.exportBackup(uri)
            // TODO: Add UI event for success message
        } catch (e: Exception) {
            // TODO: Add UI event for error message
        }
    }

    fun cleanOrphanFiles() = viewModelScope.launch {
        _cleanupResult.value = cleanOrphanFilesUseCase()
    }

    fun onCleanupResultDialogDismissed() {
        _cleanupResult.value = null
    }
}
