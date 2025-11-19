package com.example.wppsticker.domain.usecase

import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class GetStickerPackageWithStickersUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    operator fun invoke(packageId: Int) = repository.getStickerPackageWithStickers(packageId)
}
