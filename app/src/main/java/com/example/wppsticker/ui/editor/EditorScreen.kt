package com.example.wppsticker.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.wppsticker.nav.Screen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val isBusy by viewModel.isBusy.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val isCropMode by viewModel.isCropMode.collectAsState()
    val cropRect by viewModel.cropRect.collectAsState()
    val texts by viewModel.texts.collectAsState()
    val selectedTextId by viewModel.selectedTextId.collectAsState()
    val showTextDialog by viewModel.showTextDialog.collectAsState()
    val navigateToSave by viewModel.navigateToSave.collectAsState()

    var selectedCorner by remember { mutableStateOf<Corner?>(null) }

    LaunchedEffect(navigateToSave) {
        navigateToSave?.let {
            val encodedUri = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
            navController.navigate("${Screen.SaveSticker.name}/$encodedUri")
        }
    }

    if (showTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showTextDialog(false) },
            title = { Text("Add Text") },
            text = { TextField(value = textInput, onValueChange = { textInput = it }) },
            confirmButton = { Button(onClick = { viewModel.addText(textInput) }) { Text("Add") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { viewModel.showTextDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Text")
                    }
                    IconButton(onClick = { viewModel.toggleCropMode() }) {
                        Icon(Icons.Default.Create, contentDescription = "Crop Image")
                    }
                    IconButton(onClick = { viewModel.onSaveAndContinue() }) {
                        Icon(Icons.Default.Check, contentDescription = "Continue")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isCropMode, selectedTextId) {
                        detectTapGestures(
                            onTap = { touchOffset ->
                                val tappedText = texts.find { it.getBoundingBox(32f).contains(touchOffset) }
                                viewModel.onTextSelected(tappedText?.id)
                            }
                        )
                        if (isCropMode) {
                            detectDragGestures(
                                onDragStart = { touchOffset ->
                                    selectedCorner = Corner.fromTouchPoint(cropRect, touchOffset)
                                },
                                onDragEnd = { selectedCorner = null }
                            ) { change, dragAmount ->
                                selectedCorner?.let {
                                    val newRect = it.move(cropRect, dragAmount)
                                    viewModel.updateCropRect(newRect)
                                }
                                change.consume()
                            }
                        } else if (selectedTextId != null) {
                            detectTransformGestures { _, pan, zoom, gestureRotation ->
                                viewModel.updateSelectedText(offset = pan, scale = zoom, rotation = gestureRotation)
                            }
                        } else {
                            detectTransformGestures { _, pan, zoom, gestureRotation ->
                                viewModel.onOffsetChanged(pan)
                                viewModel.onScaleChanged(zoom)
                                viewModel.onRotationChanged(gestureRotation)
                            }
                        }
                    }
            ) {
                imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotation,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }

                texts.forEach { textData ->
                    val borderModifier = if (textData.id == selectedTextId) {
                        Modifier.border(2.dp, Color.Yellow)
                    } else {
                        Modifier
                    }
                    Text(
                        text = textData.text,
                        color = textData.color,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .then(borderModifier)
                            .padding(4.dp)
                            .graphicsLayer(
                                scaleX = textData.scale,
                                scaleY = textData.scale,
                                rotationZ = textData.rotation,
                                translationX = textData.offset.x,
                                translationY = textData.offset.y
                            )
                            .clickable { viewModel.onTextSelected(textData.id) }
                    )
                }

                if (isCropMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color.White,
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            style = Stroke(width = 2f)
                        )
                        drawCircle(Color.White, radius = 20f, center = cropRect.topLeft)
                        drawCircle(Color.White, radius = 20f, center = cropRect.topRight)
                        drawCircle(Color.White, radius = 20f, center = cropRect.bottomLeft)
                        drawCircle(Color.White, radius = 20f, center = cropRect.bottomRight)
                    }
                }
            }

            val selectedText = texts.find { it.id == selectedTextId }
            if (selectedText != null) {
                ColorSelector(
                    selectedColor = selectedText.color,
                    onColorSelected = { color -> viewModel.updateSelectedTextColor(color) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
            
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun ColorSelector(selectedColor: Color, onColorSelected: (Color) -> Unit, modifier: Modifier = Modifier) {
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { color ->
            val border = if (color == selectedColor) {
                Modifier.border(3.dp, Color.White, CircleShape)
            } else {
                Modifier.border(1.dp, Color.Gray, CircleShape)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(border)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

enum class Corner {
    TopLeft, TopRight, BottomLeft, BottomRight;

    fun move(rect: Rect, dragAmount: Offset): Rect {
        return when (this) {
            TopLeft -> Rect(rect.left + dragAmount.x, rect.top + dragAmount.y, rect.right, rect.bottom)
            TopRight -> Rect(rect.left, rect.top + dragAmount.y, rect.right + dragAmount.x, rect.bottom)
            BottomLeft -> Rect(rect.left + dragAmount.x, rect.top, rect.right, rect.bottom + dragAmount.y)
            BottomRight -> Rect(rect.left, rect.top, rect.right + dragAmount.x, rect.bottom + dragAmount.y)
        }
    }

    companion object {
        fun fromTouchPoint(rect: Rect, touchPoint: Offset): Corner? {
            val touchRadius = 40f
            return when {
                abs(touchPoint.x - rect.left) < touchRadius && abs(touchPoint.y - rect.top) < touchRadius -> TopLeft
                abs(touchPoint.x - rect.right) < touchRadius && abs(touchPoint.y - rect.top) < touchRadius -> TopRight
                abs(touchPoint.x - rect.left) < touchRadius && abs(touchPoint.y - rect.bottom) < touchRadius -> BottomLeft
                abs(touchPoint.x - rect.right) < touchRadius && abs(touchPoint.y - rect.bottom) < touchRadius -> BottomRight
                else -> null
            }
        }
    }
}

fun TextData.getBoundingBox(fontSize: Float): Rect {
    // Simplified calculation
    val width = text.length * fontSize * 0.6f 
    return Rect(center = offset, radius = width)
}
