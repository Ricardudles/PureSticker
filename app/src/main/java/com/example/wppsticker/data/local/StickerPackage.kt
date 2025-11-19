package com.example.wppsticker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sticker_packages")
data class StickerPackage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val identifier: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String,
    val trayImageFile: String,
    val imageDataVersion: String = "1",
    // New optional fields for better customization
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = ""
)
