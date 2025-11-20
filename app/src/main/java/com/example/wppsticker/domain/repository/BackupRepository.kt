package com.example.wppsticker.domain.repository

import android.net.Uri
import com.example.wppsticker.data.backup.BackupFileDto

interface BackupRepository {
    suspend fun exportBackup(uri: Uri)
    suspend fun inspectBackup(uri: Uri): BackupFileDto
    suspend fun restoreBackup(uri: Uri, selectedPackageIdentifiers: List<String>)
}
