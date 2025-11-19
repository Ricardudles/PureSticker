package com.example.wppsticker.domain.usecase

import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class CreateStickerPackageUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(stickerPackage: StickerPackage) = repository.insertStickerPackage(stickerPackage)
}
