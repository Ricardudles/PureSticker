package com.example.wppsticker.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class StickerPackageWithStickers(
    @Embedded val stickerPackage: StickerPackage,
    @Relation(
        parentColumn = "id",
        entityColumn = "packageId"
    )
    val stickers: List<Sticker>
)
