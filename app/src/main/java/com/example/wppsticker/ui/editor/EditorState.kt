package com.example.wppsticker.ui.editor

import android.net.Uri

data class EditorState(
    val imageState: ImageState = ImageState(),
    val texts: List<TextData> = emptyList(),
    val imageUri: Uri? = null
)
