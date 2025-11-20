package com.example.wppsticker.data.repository

import android.content.Context
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.data.local.StickerDao
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.data.local.StickerPackageWithStickers
import com.example.wppsticker.domain.repository.StickerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StickerRepositoryImpl @Inject constructor(
    private val stickerDao: StickerDao,
    @ApplicationContext private val context: Context
) : StickerRepository {

    override fun getStickerPackages(): Flow<List<StickerPackage>> {
        return stickerDao.getStickerPackages()
    }

    override fun getStickerPackagesWithStickers(): Flow<List<StickerPackageWithStickers>> {
        return stickerDao.getStickerPackagesWithStickers()
    }

    override suspend fun getStickerPackagesWithStickersSync(): List<StickerPackageWithStickers> {
        return stickerDao.getStickerPackagesWithStickersSync()
    }
    
    override fun getStickerPackagesSync(): List<StickerPackage> {
        return stickerDao.getStickerPackagesSync()
    }

    override fun getStickerPackageWithStickers(packageId: Int): Flow<StickerPackageWithStickers> {
        return stickerDao.getStickerPackageWithStickers(packageId)
    }

    override fun getStickerPackageWithStickersSync(packageId: Int): StickerPackageWithStickers? {
        return stickerDao.getStickerPackageWithStickersSync(packageId)
    }
    
    override fun getStickerPackageWithStickersByIdentifierSync(identifier: String): StickerPackageWithStickers? {
        return stickerDao.getStickerPackageWithStickersByIdentifierSync(identifier)
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

    override suspend fun isStickerPackageWhitelisted(identifier: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val authority = "${context.packageName}.provider"
                val contentProviderAuthority = "com.whatsapp.provider.sticker_whitelist_check"
                
                val uri = android.net.Uri.parse("content://$contentProviderAuthority/is_whitelisted")
                    .buildUpon()
                    .appendQueryParameter("authority", authority)
                    .appendQueryParameter("identifier", identifier)
                    .build()

                val cursor = context.contentResolver.query(uri, null, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val isWhitelisted = it.getInt(it.getColumnIndexOrThrow("result")) == 1
                        return@withContext isWhitelisted
                    }
                }
                
                // Try WhatsApp Business if not found or error
                val contentProviderAuthorityBusiness = "com.whatsapp.w4b.provider.sticker_whitelist_check"
                val uriBusiness = android.net.Uri.parse("content://$contentProviderAuthorityBusiness/is_whitelisted")
                    .buildUpon()
                    .appendQueryParameter("authority", authority)
                    .appendQueryParameter("identifier", identifier)
                    .build()
                    
                val cursorBusiness = context.contentResolver.query(uriBusiness, null, null, null, null)
                cursorBusiness?.use {
                    if (it.moveToFirst()) {
                        val isWhitelisted = it.getInt(it.getColumnIndexOrThrow("result")) == 1
                        return@withContext isWhitelisted
                    }
                }
                
                return@withContext false
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    override suspend fun getAllStickerFileNames(): List<String> {
        return stickerDao.getAllStickerFileNames()
    }

    override suspend fun getAllTrayIconFileNames(): List<String> {
        return stickerDao.getAllTrayIconFileNames()
    }
}
