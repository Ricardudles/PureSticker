package com.example.wppsticker.data

/** Emoji tags sent to WhatsApp must contain at least one emoji on Android. */
object StickerEmojiPolicy {
    const val DEFAULT_WHATSAPP_EMOJI = "🙂"
    const val MAX_EMOJIS_PER_STICKER = 3

    fun withDefaultIfEmpty(emojis: List<String>): List<String> =
        emojis.map(String::trim).filter(String::isNotEmpty)
            .ifEmpty { listOf(DEFAULT_WHATSAPP_EMOJI) }
}
