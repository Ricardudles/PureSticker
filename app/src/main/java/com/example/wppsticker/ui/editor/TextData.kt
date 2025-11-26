package com.example.wppsticker.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.UUID

data class TextData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val color: Color = Color.White,
    val fontIndex: Int = 0 // 0: Default, 1: Serif, 2: Mono, 3: Cursive, etc.
) {
    fun getBoundingBox(fontSize: Float): Rect {
        val estimatedWidth = text.length * fontSize * 0.6f
        val estimatedHeight = fontSize
        return Rect(
            left = offset.x - estimatedWidth / 2f,
            top = offset.y - estimatedHeight / 2f,
            right = offset.x + estimatedWidth / 2f,
            bottom = offset.y + estimatedHeight / 2f
        )
    }
}
