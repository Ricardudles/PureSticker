package com.example.wppsticker.domain.repository

import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.data.local.StickerPackageWithStickers
import kotlinx.coroutines.flow.Flow

interface StickerRepository {

    fun getStickerPackages(): Flow<List<StickerPackage>>
    
    fun getStickerPackagesSync(): List<StickerPackage>

    fun getStickerPackageWithStickers(packageId: Int): Flow<StickerPackageWithStickers>

    fun getStickerPackageWithStickersSync(packageId: Int): StickerPackageWithStickers?
    
    fun getStickerPackageWithStickersByIdentifierSync(identifier: String): StickerPackageWithStickers?

    suspend fun insertStickerPackage(stickerPackage: StickerPackage): Long

    suspend fun updateStickerPackage(stickerPackage: StickerPackage)

    suspend fun insertSticker(sticker: Sticker)

    suspend fun deleteStickerPackage(packageId: Int)

    suspend fun deleteSticker(stickerId: Int)

    suspend fun isStickerPackageWhitelisted(identifier: String): Boolean
}
