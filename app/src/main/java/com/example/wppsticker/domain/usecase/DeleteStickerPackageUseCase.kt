package com.example.wppsticker.domain.usecase

import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class DeleteStickerPackageUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(packageId: Int) = repository.deleteStickerPackage(packageId)
}
