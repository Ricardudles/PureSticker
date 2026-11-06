package com.example.wppsticker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Keeps the 512×512 sticker edge visible above both dark and bright media. */
@Composable
fun StickerCanvasBoundary(modifier: Modifier = Modifier) {
    val outerColor = MaterialTheme.colorScheme.outlineVariant
    val innerColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = outerColor, style = Stroke(width = 2.dp.toPx()))

        val inset = 2.dp.toPx()
        val innerSize = Size(
            width = (size.width - inset * 2).coerceAtLeast(0f),
            height = (size.height - inset * 2).coerceAtLeast(0f)
        )
        drawRect(
            color = innerColor,
            topLeft = Offset(inset, inset),
            size = innerSize,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
