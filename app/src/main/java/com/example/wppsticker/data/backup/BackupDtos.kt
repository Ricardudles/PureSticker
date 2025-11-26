package com.example.wppsticker.data.backup

/**
 * Data Transfer Objects (DTOs) used for serializing backup data to JSON.
 * These are decoupled from the Room entities to make the backup format stable over time.
 */

data class BackupFileDto(
    val appVersion: Int,
    val backupDate: Long,
    val packages: List<BackupPackageDto>
)

data class BackupPackageDto(
    val identifier: String,
    val name: String,
    val author: String,
    val trayImageFile: String,
    val imageDataVersion: String,
    val publisherEmail: String,
    val publisherWebsite: String,
    val privacyPolicyWebsite: String,
    val licenseAgreementWebsite: String,
    val isAnimated: Boolean = false, // Added for animated stickers, default to false for old backups
    val stickers: List<BackupStickerDto>
)

data class BackupStickerDto(
    val imageFile: String,
    val imageFileHash: String? = null, // Added nullable for backward compatibility
    val emojis: List<String>
)
