package com.example.wppsticker.domain.usecase

import android.content.Context
import com.example.wppsticker.domain.repository.StickerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class CleanupResult(val filesDeleted: Int, val spaceFreedKb: Long)

class CleanOrphanFilesUseCase @Inject constructor(
    private val stickerRepository: StickerRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(): CleanupResult = withContext(Dispatchers.IO) {
        val validDbFiles = stickerRepository.getAllStickerFileNames() + stickerRepository.getAllTrayIconFileNames()
        val validFileNames = validDbFiles.filter { it.isNotEmpty() }.toSet()

        val allPhysicalFiles = context.filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".webp")
        } ?: emptyArray()

        var filesDeleted = 0
        var spaceFreed: Long = 0

        allPhysicalFiles.forEach { file ->
            if (!validFileNames.contains(file.name)) {
                val fileSize = file.length()
                if (file.delete()) {
                    filesDeleted++
                    spaceFreed += fileSize
                }
            }
        }

        CleanupResult(filesDeleted, spaceFreed / 1024)
    }
}
