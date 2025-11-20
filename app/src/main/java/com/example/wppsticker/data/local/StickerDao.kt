package com.example.wppsticker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {

    @Insert
    suspend fun insertStickerPackage(stickerPackage: StickerPackage): Long

    @Update
    suspend fun updateStickerPackage(stickerPackage: StickerPackage)

    @Insert
    suspend fun insertSticker(sticker: Sticker)

    @Query("SELECT * FROM sticker_packages")
    fun getStickerPackages(): Flow<List<StickerPackage>>

    // Flow version for UI
    @Transaction
    @Query("SELECT * FROM sticker_packages")
    fun getStickerPackagesWithStickers(): Flow<List<StickerPackageWithStickers>>

    // Sync/Suspend version for Backup
    @Transaction
    @Query("SELECT * FROM sticker_packages")
    suspend fun getStickerPackagesWithStickersSync(): List<StickerPackageWithStickers>

    @Query("SELECT * FROM sticker_packages")
    fun getStickerPackagesSync(): List<StickerPackage>

    @Transaction
    @Query("SELECT * FROM sticker_packages WHERE id = :packageId")
    fun getStickerPackageWithStickers(packageId: Int): Flow<StickerPackageWithStickers>

    @Transaction
    @Query("SELECT * FROM sticker_packages WHERE id = :packageId")
    fun getStickerPackageWithStickersSync(packageId: Int): StickerPackageWithStickers?
    
    @Transaction
    @Query("SELECT * FROM sticker_packages WHERE identifier = :identifier")
    fun getStickerPackageWithStickersByIdentifierSync(identifier: String): StickerPackageWithStickers?

    @Query("DELETE FROM sticker_packages WHERE id = :packageId")
    suspend fun deleteStickerPackage(packageId: Int)

    @Query("DELETE FROM stickers WHERE id = :stickerId")
    suspend fun deleteSticker(stickerId: Int)

    // --- For Orphan File Cleanup ---
    @Query("SELECT imageFile FROM stickers")
    suspend fun getAllStickerFileNames(): List<String>

    @Query("SELECT trayImageFile FROM sticker_packages")
    suspend fun getAllTrayIconFileNames(): List<String>
}
