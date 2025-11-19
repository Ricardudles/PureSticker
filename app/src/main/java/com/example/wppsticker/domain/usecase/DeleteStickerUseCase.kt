package com.example.wppsticker.domain.usecase

import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class DeleteStickerUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(stickerId: Int) = repository.deleteSticker(stickerId)
}
