package com.example.wppsticker.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.UUID

data class TextData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var offset: Offset = Offset.Zero,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var color: Color = Color.White
)
