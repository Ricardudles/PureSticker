package com.example.wppsticker.ui.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wppsticker.data.backup.BackupPackageDto
import com.example.wppsticker.domain.repository.BackupRepository
import com.example.wppsticker.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RestoreStatus {
    NEW, EXISTS
}

data class BackupPackageInfo(
    val backupPackage: BackupPackageDto,
    val status: RestoreStatus
)

sealed class RestoreUiState {
    object Idle : RestoreUiState()
    object Loading : RestoreUiState()
    object Success : RestoreUiState()
    data class Error(val message: String) : RestoreUiState()
}

@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val backupRepository: BackupRepository,
    private val stickerRepository: StickerRepository
) : ViewModel() {

    private val backupUri: Uri = Uri.parse(checkNotNull(savedStateHandle["backupUri"]))

    private val _backupPackages = MutableStateFlow<List<BackupPackageInfo>>(emptyList())
    val backupPackages = _backupPackages.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages = _selectedPackages.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val restoreState = _restoreState.asStateFlow()

    init {
        inspectBackup()
    }

    private fun inspectBackup() = viewModelScope.launch {
        _restoreState.value = RestoreUiState.Loading
        try {
            val backupFile = backupRepository.inspectBackup(backupUri)
            val localPackages = stickerRepository.getStickerPackagesWithStickersSync()
            val localIdentifiers = localPackages.map { it.stickerPackage.identifier }.toSet()

            val backupInfo = backupFile.packages.map { backupPackage ->
                val status = if (localIdentifiers.contains(backupPackage.identifier)) {
                    RestoreStatus.EXISTS
                } else {
                    RestoreStatus.NEW
                }
                BackupPackageInfo(backupPackage, status)
            }
            _backupPackages.value = backupInfo
            _restoreState.value = RestoreUiState.Idle

        } catch (e: Exception) {
            _restoreState.value = RestoreUiState.Error(e.message ?: "Failed to read backup file.")
        }
    }

    fun toggleSelection(identifier: String) {
        _backupPackages.value.find { it.backupPackage.identifier == identifier }?.let {
            if (it.status == RestoreStatus.NEW) {
                _selectedPackages.update {
                    if (it.contains(identifier)) it - identifier else it + identifier
                }
            }
        }
    }

    fun restoreSelected() = viewModelScope.launch {
        _restoreState.value = RestoreUiState.Loading
        try {
            backupRepository.restoreBackup(backupUri, selectedPackages.value.toList())
            _restoreState.value = RestoreUiState.Success
        } catch (e: Exception) {
            _restoreState.value = RestoreUiState.Error(e.message ?: "Failed to restore packages.")
        }
    }
}
