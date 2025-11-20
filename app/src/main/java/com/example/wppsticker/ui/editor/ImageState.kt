package com.example.wppsticker.ui.editor

import androidx.compose.ui.geometry.Offset

data class ImageState(
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f
)
