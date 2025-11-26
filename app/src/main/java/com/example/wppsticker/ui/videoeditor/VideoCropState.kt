package com.example.wppsticker.ui.videoeditor

import androidx.compose.ui.geometry.Offset

data class VideoCropState(
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f
)
