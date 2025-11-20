package com.example.wppsticker.data.repository

import android.content.Context
import android.net.Uri
import com.example.wppsticker.data.backup.BackupFileDto
import com.example.wppsticker.data.backup.BackupPackageDto
import com.example.wppsticker.data.backup.BackupStickerDto
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.domain.repository.BackupRepository
import com.example.wppsticker.domain.repository.StickerRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Named

class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stickerRepository: StickerRepository,
    @Named("AppVersionCode") private val appVersionCode: Int
) : BackupRepository {

    private val gson = Gson()

    override suspend fun exportBackup(uri: Uri) {
        withContext(Dispatchers.IO) {
            val packagesWithStickers = stickerRepository.getStickerPackagesWithStickersSync()

            val backupPackages = packagesWithStickers.map { packWithStickers ->
                BackupPackageDto(
                    identifier = packWithStickers.stickerPackage.identifier,
                    name = packWithStickers.stickerPackage.name,
                    author = packWithStickers.stickerPackage.author,
                    trayImageFile = packWithStickers.stickerPackage.trayImageFile,
                    imageDataVersion = packWithStickers.stickerPackage.imageDataVersion,
                    publisherEmail = packWithStickers.stickerPackage.publisherEmail,
                    publisherWebsite = packWithStickers.stickerPackage.publisherWebsite,
                    privacyPolicyWebsite = packWithStickers.stickerPackage.privacyPolicyWebsite,
                    licenseAgreementWebsite = packWithStickers.stickerPackage.licenseAgreementWebsite,
                    stickers = packWithStickers.stickers.map { sticker ->
                        BackupStickerDto(
                            imageFile = sticker.imageFile, 
                            imageFileHash = sticker.imageFileHash,
                            emojis = sticker.emojis
                        )
                    }
                )
            }

            val backupFileDto = BackupFileDto(
                appVersion = appVersionCode,
                backupDate = System.currentTimeMillis(),
                packages = backupPackages
            )

            val jsonString = gson.toJson(backupFileDto)

            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                ZipOutputStream(FileOutputStream(pfd.fileDescriptor)).use { zos ->
                    // Write metadata file
                    zos.putNextEntry(ZipEntry("backup_metadata.json"))
                    zos.write(jsonString.toByteArray())
                    zos.closeEntry()

                    // Write image files
                    packagesWithStickers.forEach { pack ->
                        val allImages = pack.stickers.map { it.imageFile } + pack.stickerPackage.trayImageFile
                        allImages.filter { it.isNotEmpty() }.forEach { fileName ->
                            val file = File(context.filesDir, fileName)
                            if (file.exists()) {
                                FileInputStream(file).use { fis ->
                                    zos.putNextEntry(ZipEntry("images/$fileName"))
                                    fis.copyTo(zos)
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun inspectBackup(uri: Uri): BackupFileDto = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "backup_metadata.json") {
                        val reader = BufferedReader(InputStreamReader(zis))
                        return@withContext gson.fromJson(reader, BackupFileDto::class.java)
                    }
                    entry = zis.nextEntry
                }
            }
        }
        throw IllegalStateException("backup_metadata.json not found in the backup file.")
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun restoreBackup(uri: Uri, selectedPackageIdentifiers: List<String>) = withContext(Dispatchers.IO) {
        val backupDto = inspectBackup(uri)
        val packagesToRestore = backupDto.packages.filter { selectedPackageIdentifiers.contains(it.identifier) }
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("images/")) {
                        val fileName = entry.name.substringAfter("images/")
                        val isNeeded = packagesToRestore.any { it.stickers.any { s -> s.imageFile == fileName } || it.trayImageFile == fileName }

                        if (isNeeded) {
                            val outputFile = File(context.filesDir, fileName)
                            FileOutputStream(outputFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        packagesToRestore.forEach { backupPackage ->
            val newPackage = StickerPackage(
                identifier = backupPackage.identifier,
                name = backupPackage.name,
                author = backupPackage.author,
                trayImageFile = backupPackage.trayImageFile,
                imageDataVersion = backupPackage.imageDataVersion,
                publisherEmail = backupPackage.publisherEmail,
                publisherWebsite = backupPackage.publisherWebsite,
                privacyPolicyWebsite = backupPackage.privacyPolicyWebsite,
                licenseAgreementWebsite = backupPackage.licenseAgreementWebsite
            )
            val newPackageId = stickerRepository.insertStickerPackage(newPackage).toInt()

            backupPackage.stickers.forEach { backupSticker ->
                val stickerFile = File(context.filesDir, backupSticker.imageFile)
                
                // Logic for backward compatibility or integrity:
                // If hash is missing in backup, calculate it now from the restored file.
                val finalHash = if (backupSticker.imageFileHash.isNullOrEmpty()) {
                    if (stickerFile.exists()) {
                        calculateFileHash(stickerFile)
                    } else {
                        "" // Should ideally not happen if restore flow is correct
                    }
                } else {
                    backupSticker.imageFileHash
                }

                val newSticker = Sticker(
                    packageId = newPackageId,
                    imageFile = backupSticker.imageFile,
                    imageFileHash = finalHash,
                    emojis = backupSticker.emojis,
                    width = 512, // Assume standard size
                    height = 512,
                    sizeInKb = if (stickerFile.exists()) stickerFile.length() / 1024 else 0
                )
                stickerRepository.insertSticker(newSticker)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun calculateFileHash(file: File): String {
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
}
