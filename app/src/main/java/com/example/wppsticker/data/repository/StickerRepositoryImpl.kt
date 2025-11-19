package com.example.wppsticker.data.repository

import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerDao
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.data.local.StickerPackageWithStickers
import com.example.wppsticker.domain.repository.StickerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StickerRepositoryImpl @Inject constructor(
    private val stickerDao: StickerDao
) : StickerRepository {

    override fun getStickerPackages(): Flow<List<StickerPackage>> {
        return stickerDao.getStickerPackages()
    }

    override fun getStickerPackageWithStickers(packageId: Int): Flow<StickerPackageWithStickers> {
        return stickerDao.getStickerPackageWithStickers(packageId)
    }

    override fun getStickerPackageWithStickersSync(packageId: Int): StickerPackageWithStickers? {
        return stickerDao.getStickerPackageWithStickersSync(packageId)
    }

    override suspend fun insertStickerPackage(stickerPackage: StickerPackage): Long {
        return stickerDao.insertStickerPackage(stickerPackage)
    }

    override suspend fun updateStickerPackage(stickerPackage: StickerPackage) {
        stickerDao.updateStickerPackage(stickerPackage)
    }

    override suspend fun insertSticker(sticker: Sticker) {
        stickerDao.insertSticker(sticker)
    }

    override suspend fun deleteStickerPackage(packageId: Int) {
        stickerDao.deleteStickerPackage(packageId)
    }

    override suspend fun deleteSticker(stickerId: Int) {
        stickerDao.deleteSticker(stickerId)
    }
}
