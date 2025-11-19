package com.example.wppsticker.domain.usecase

import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.domain.repository.StickerRepository
import javax.inject.Inject

class AddStickerUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(sticker: Sticker) = repository.insertSticker(sticker)
}
