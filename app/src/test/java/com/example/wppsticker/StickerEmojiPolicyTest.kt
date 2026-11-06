package com.example.wppsticker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerEmojiPolicyTest {
    @Test
    fun emptySelectionGetsCompatibilityEmoji() {
        assertEquals(
            listOf(StickerEmojiPolicy.DEFAULT_WHATSAPP_EMOJI),
            StickerEmojiPolicy.withDefaultIfEmpty(emptyList())
        )
    }

    @Test
    fun selectedEmojisArePreserved() {
        val selected = listOf(" 😀 ", "❤️")

        assertEquals(listOf("😀", "❤️"), StickerEmojiPolicy.withDefaultIfEmpty(selected))
    }
}
