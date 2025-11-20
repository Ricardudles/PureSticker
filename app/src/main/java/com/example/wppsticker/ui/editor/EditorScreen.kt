package com.example.wppsticker.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.example.wppsticker.nav.Screen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val isBusy by viewModel.isBusy.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val imageState by viewModel.imageState.collectAsState()
    val texts by viewModel.texts.collectAsState()
    val selectedTextId by viewModel.selectedTextId.collectAsState()
    val showTextDialog by viewModel.showTextDialog.collectAsState()
    val navigateToSave by viewModel.navigateToSave.collectAsState()
    
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                viewModel.onImageCropped(uriContent)
            }
        }
    }

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
                title = { Text("Edit Sticker") },
                actions = {
                    IconButton(onClick = { viewModel.showTextDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Text")
                    }
                    IconButton(onClick = { viewModel.onSaveAndContinue() }) {
                        Icon(Icons.Default.Check, contentDescription = "Continue")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.DarkGray) // Dark background for contrast
        ) {
            // Workspace Area
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // We want a square workspace that fits within the available space
                val workspaceSize = minOf(maxWidth, maxHeight)
                val density = LocalDensity.current
                
                // Mapping factors to convert between screen pixels and canvas (512x512)
                val canvasSize = 512f
                val scaleToCanvas = remember(workspaceSize) { canvasSize / with(density) { workspaceSize.toPx() } }
                val scaleFromCanvas = remember(workspaceSize) { with(density) { workspaceSize.toPx() } / canvasSize }

                Box(
                    modifier = Modifier
                        .size(workspaceSize)
                        .aspectRatio(1f)
                        .background(Color.Transparent) // The sticker background is transparent usually
                ) {
                    // 1. Guide/Border for the sticker area
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color.White,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }

                    // 2. Image Layer with Transforms
                    // We clip content to the box so it looks like the final cut
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    viewModel.onTextSelected(null)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    // Check directly from ViewModel to avoid stale capture in closure
                                    if (viewModel.selectedTextId.value == null) {
                                        // Apply transforms to image if no text is selected
                                        // Convert pan from screen pixels to canvas units (512x512)
                                        val canvasPan = pan * scaleToCanvas
                                        viewModel.updateImageState(offset = canvasPan, scale = zoom, rotation = rotation)
                                    }
                                }
                            }
                    ) {
                        imageUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Sticker Image",
                                modifier = Modifier
                                    .fillMaxSize() // Coil usually fits/fills. We want it centered initially.
                                    .graphicsLayer(
                                        scaleX = imageState.scale,
                                        scaleY = imageState.scale,
                                        rotationZ = imageState.rotation,
                                        translationX = imageState.offset.x * scaleFromCanvas, // Convert back to screen pixels for display
                                        translationY = imageState.offset.y * scaleFromCanvas
                                    )
                            )
                        }
                    }

                    // 3. Text Layer
                    texts.forEach { textData ->
                        val isSelected = textData.id == selectedTextId
                        val borderModifier = if (isSelected) {
                            Modifier.border(1.dp, Color.Yellow)
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center) // Start from center
                                .graphicsLayer(
                                    scaleX = textData.scale,
                                    scaleY = textData.scale,
                                    rotationZ = textData.rotation,
                                    translationX = textData.offset.x * scaleFromCanvas,
                                    translationY = textData.offset.y * scaleFromCanvas
                                )
                                .then(borderModifier)
                                .pointerInput(textData.id) {
                                    detectTapGestures {
                                        viewModel.onTextSelected(textData.id)
                                    }
                                }
                                .pointerInput(textData.id, isSelected) {
                                    if (isSelected) {
                                        detectTransformGestures { _, pan, zoom, rotation ->
                                            val canvasPan = pan * scaleToCanvas
                                            viewModel.updateSelectedText(
                                                offset = canvasPan,
                                                scale = zoom,
                                                rotation = rotation
                                            )
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = textData.text,
                                color = textData.color,
                                // Font size needs to be relative to the workspace size to match canvas 32f
                                // 32f is 32/512 = 0.0625 of the canvas size
                                fontSize = (workspaceSize.value * 0.0625f).sp, // Approximate visual matching
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }

            // Controls (Color Selector)
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
