package com.example.wppsticker.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.example.wppsticker.nav.Screen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val isSnapEnabled by viewModel.isSnapEnabled.collectAsState()
    val snapStrength by viewModel.snapStrength.collectAsState()
    
    var showSnapMenu by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept Back Button
    BackHandler {
        showExitDialog = true
    }

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
            viewModel.onNavigatedToSave() // Reset navigation event
        }
    }

    // Exit Confirmation Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sair da edição?") },
            text = { Text("Você perderá todas as alterações não salvas. Tem certeza?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Sair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showTextDialog(false) },
            title = { Text("Add Text") },
            text = { 
                TextField(
                    value = textInput, 
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type something...") },
                    singleLine = true
                ) 
            },
            confirmButton = { 
                Button(
                    onClick = { viewModel.addText(textInput) },
                    enabled = textInput.isNotBlank() // Disable if empty
                ) { 
                    Text("Add") 
                } 
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showTextDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Sticker") },
                actions = {
                    // Only show top actions if NO text is selected (to avoid clutter)
                    if (selectedTextId == null) {
                        // Snap Toggle
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = { viewModel.toggleSnap(!isSnapEnabled) },
                                        onLongClick = { showSnapMenu = true }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSnapEnabled) Icons.Default.Grid3x3 else Icons.Default.GridOff,
                                    contentDescription = if (isSnapEnabled) "Snap On" else "Snap Off",
                                    tint = if (isSnapEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showSnapMenu,
                                onDismissRequest = { showSnapMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Magnet Strength", style = MaterialTheme.typography.titleSmall) },
                                    onClick = {},
                                    enabled = false
                                )
                                val strengths = listOf(
                                    1 to "1 - Very Weak",
                                    2 to "2 - Weak",
                                    3 to "3 - Standard",
                                    4 to "4 - Strong",
                                    5 to "5 - Very Strong"
                                )
                                strengths.forEach { (level, label) ->
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (level == snapStrength) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.size(8.dp))
                                                } else {
                                                    Spacer(modifier = Modifier.size(24.dp))
                                                }
                                                Text(label)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSnapStrength(level)
                                            showSnapMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        // Add Text
                        IconButton(onClick = { viewModel.showTextDialog(true) }) {
                            Icon(Icons.Default.Title, contentDescription = "Add Text")
                        }
                        // Save
                        IconButton(onClick = { viewModel.onSaveAndContinue() }) {
                            Icon(Icons.Default.Check, contentDescription = "Continue")
                        }
                    } else {
                        // When text is selected, show a "Done" button in top bar (optional, mainly bottom bar handles it)
                        // Or keep it clean. Let's keep top bar clean or show "Done" to exit text mode
                        Button(
                            onClick = { viewModel.onTextSelected(null) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.DarkGray)
        ) {
            // Workspace Area
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(bottom = 160.dp), // Reserve space for bottom panel
                contentAlignment = Alignment.Center
            ) {
                val workspaceSize = minOf(maxWidth, maxHeight)
                val density = LocalDensity.current
                
                val canvasSize = 512f
                val scaleToCanvas = remember(workspaceSize) { canvasSize / with(density) { workspaceSize.toPx() } }
                val scaleFromCanvas = remember(workspaceSize) { with(density) { workspaceSize.toPx() } / canvasSize }
                
                // Calculate font size in SP so it visually matches the 32px base size on the 512px canvas
                // ignoring the user's font scaling preference to ensure WYSIWYG.
                val textFontSize = remember(workspaceSize) {
                    with(density) {
                        val targetHeightPx = 32f * scaleFromCanvas
                        targetHeightPx.toSp()
                    }
                }

                Box(
                    modifier = Modifier
                        .size(workspaceSize)
                        .aspectRatio(1f)
                        .background(Color.Transparent)
                        .clipToBounds() 
                ) {
                    // 1. Guide/Border
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color.White,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }

                    // 2. Image Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    viewModel.onTextSelected(null)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    if (viewModel.selectedTextId.value == null) {
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
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = imageState.scale
                                        scaleY = imageState.scale
                                        rotationZ = imageState.rotation
                                        translationX = imageState.offset.x * scaleFromCanvas
                                        translationY = imageState.offset.y * scaleFromCanvas
                                    }
                            )
                        }
                    }

                    // 3. Text Layer
                    texts.forEach { textData ->
                        key(textData.id) {
                            val isSelected = textData.id == selectedTextId
                            val borderModifier = if (isSelected) {
                                Modifier.border(1.dp, Color.Yellow)
                            } else {
                                Modifier
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        scaleX = textData.scale
                                        scaleY = textData.scale
                                        rotationZ = textData.rotation
                                        translationX = textData.offset.x * scaleFromCanvas
                                        translationY = textData.offset.y * scaleFromCanvas
                                    }
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
                                StickerTextDisplay(
                                    text = textData.text,
                                    color = textData.color,
                                    fontSize = textFontSize,
                                    fontIndex = textData.fontIndex
                                )
                            }
                        }
                    }
                }
            }

            // --- Unified Bottom Control Panel ---
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                val selectedText = texts.find { it.id == selectedTextId }

                if (selectedText != null) {
                    // === TEXT EDITING MODE ===
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Edit Text", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { viewModel.onTextSelected(null) }) { // OK Button
                            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color.Green)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Font Selector
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0, 1, 2, 3, 4)) { index ->
                            FontSelectorItem(
                                index = index, 
                                isSelected = selectedText.fontIndex == index,
                                onClick = { viewModel.updateSelectedTextFont(index) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Size Slider
                    Text("Size", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = selectedText.scale,
                        onValueChange = { viewModel.setTextScale(it) },
                        valueRange = 0.5f..5f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Color Selector
                    ColorSelector(
                        selectedColor = selectedText.color,
                        onColorSelected = { color -> viewModel.updateSelectedTextColor(color) }
                    )
                } else if (isSnapEnabled) {
                    // === IMAGE EDITING MODE (SNAP ON) ===
                    Text(
                        text = "Magnet Strength",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    StrengthSelector(
                        currentStrength = snapStrength,
                        onStrengthSelected = { viewModel.setSnapStrength(it) }
                    )
                } else {
                    // === IDLE MODE ===
                    Box(modifier = Modifier.height(50.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                         Text("Tap an item to edit", color = Color.Gray)
                    }
                }
            }
            
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun StickerTextDisplay(text: String, color: Color, fontSize: TextUnit, fontIndex: Int) {
    val fontFamily = when(fontIndex) {
        1 -> FontFamily.Serif
        2 -> FontFamily.Monospace
        3 -> FontFamily.Cursive
        else -> FontFamily.Default
    }
    val fontWeight = if (fontIndex == 4) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (fontIndex == 3) FontStyle.Italic else FontStyle.Normal

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        modifier = Modifier.padding(4.dp),
        lineHeight = fontSize // Ensure line height matches font size for compact rendering
    )
}

@Composable
fun FontSelectorItem(index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val label = when(index) {
        0 -> "Default"
        1 -> "Serif"
        2 -> "Mono"
        3 -> "Cursive"
        4 -> "Bold"
        else -> "?"
    }
    val border = if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .then(border)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White)
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

@Composable
fun StrengthSelector(currentStrength: Int, onStrengthSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val levels = (1..5).toList()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        levels.forEach { level ->
            val isSelected = level == currentStrength
            val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
            val border = if (isSelected) {
                Modifier.border(2.dp, Color.White, CircleShape)
            } else {
                Modifier.border(1.dp, Color.Gray, CircleShape)
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .then(border)
                    .clickable { onStrengthSelected(level) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = level.toString(), color = textColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
