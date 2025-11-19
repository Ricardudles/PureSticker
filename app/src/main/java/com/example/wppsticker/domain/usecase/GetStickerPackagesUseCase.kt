package com.example.wppsticker.domain.usecase

import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class GetStickerPackagesUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    operator fun invoke() = repository.getStickerPackages()
}
