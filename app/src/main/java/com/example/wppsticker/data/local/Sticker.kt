package com.example.wppsticker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerPackage::class,
            parentColumns = ["id"],
            childColumns = ["packageId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Sticker(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageId: Int,
    val imageFile: String,
    val imageFileHash: String, // SHA-256 hash of the image file to detect duplicates
    val emojis: List<String>,
    val width: Int,
    val height: Int,
    val sizeInKb: Long
)
