package com.example.wppsticker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packages")
data class StickerPackage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val author: String,
    val trayImageFile: String
)
