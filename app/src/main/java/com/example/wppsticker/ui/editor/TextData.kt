package com.example.wppsticker.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.UUID

data class TextData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var offset: Offset = Offset.Zero,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var color: Color = Color.White
) {
    fun getBoundingBox(fontSize: Float): Rect {
        // Simplified calculation: approximate width based on length
        // Adjust 0.6f factor as needed for font aspect ratio
        val estimatedWidth = text.length * fontSize * 0.6f
        val estimatedHeight = fontSize
        // Since text is centered at 'offset', the rect is around that center
        return Rect(
            left = offset.x - estimatedWidth / 2f,
            top = offset.y - estimatedHeight / 2f,
            right = offset.x + estimatedWidth / 2f,
            bottom = offset.y + estimatedHeight / 2f
        )
    }
}
