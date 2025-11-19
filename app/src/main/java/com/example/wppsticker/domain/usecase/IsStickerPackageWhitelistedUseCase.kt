package com.example.wppsticker.domain.usecase

import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class IsStickerPackageWhitelistedUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(identifier: String): Boolean = repository.isStickerPackageWhitelisted(identifier)
}
