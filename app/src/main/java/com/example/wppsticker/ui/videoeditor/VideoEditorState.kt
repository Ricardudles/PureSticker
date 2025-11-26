package com.example.wppsticker.ui.videoeditor

import com.example.wppsticker.ui.editor.TextData
import com.example.wppsticker.ui.videoeditor.VideoEditorViewModel.VideoCropState

data class VideoEditorState(
    val cropState: VideoCropState = VideoCropState(),
    val texts: List<TextData> = emptyList(),
    val trimRange: LongRange = 0L..0L
)
