package com.example.wppsticker.ui.videoeditor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.example.wppsticker.R
import com.example.wppsticker.ui.editor.StickerTextDisplay
import com.example.wppsticker.ui.editor.TextEditorPanel
import com.example.wppsticker.ui.editor.ToolButton
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    navController: NavController,
    viewModel: VideoEditorViewModel = hiltViewModel()
) {
    val videoUri by viewModel.videoUri.collectAsState()
    val cropState by viewModel.cropState.collectAsState()
    val trimRange by viewModel.trimRange.collectAsState()
    val videoDuration by viewModel.videoDuration.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    
    val texts by viewModel.texts.collectAsState()
    val selectedTextId by viewModel.selectedTextId.collectAsState()
    val showTextDialog by viewModel.showTextDialog.collectAsState()
    
    val isBusy by viewModel.isBusy.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val navigateToSave by viewModel.navigateToSave.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()

    // Undo/Redo States
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    
    // Retrieve packageId to forward it
    val packageIdArg = remember { 
        navController.currentBackStackEntry?.arguments?.getInt("packageId", -1) ?: -1
    }

    BackHandler { showExitDialog = true }

    LaunchedEffect(navigateToSave) {
        navigateToSave?.let { route ->
            // Append isAnimated=true for video stickers
            var finalRoute = "$route&isAnimated=true"
            // Append packageId if present (though ViewModel might have added it, let's ensure)
            if (packageIdArg != -1 && !finalRoute.contains("packageId=")) {
                finalRoute += "&packageId=$packageIdArg"
            }
            navController.navigate(finalRoute)
            viewModel.onNavigatedToSave()
        }
    }

    // --- DIALOGS ---
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_editor_title)) },
            text = { Text(stringResource(R.string.exit_editor_msg)) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; navController.popBackStack() }) {
                    Text(stringResource(R.string.exit), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    
    if (showTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showTextDialog(false) },
            title = { Text(stringResource(R.string.new_text_title)) },
            text = { 
                TextField(
                    value = textInput, 
                    onValueChange = { textInput = it },
                    placeholder = { Text(stringResource(R.string.type_here_placeholder)) },
                    singleLine = true
                ) 
            },
            confirmButton = { 
                Button(onClick = { viewModel.addText(textInput) }, enabled = textInput.isNotBlank()) { 
                    Text(stringResource(R.string.add)) 
                } 
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showTextDialog(false) }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    // Undo Button
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.undo),
                            tint = if (canUndo) Color.White else Color.Gray
                        )
                    }
                    // Redo Button
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.redo),
                            tint = if (canRedo) Color.White else Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = { viewModel.onSave() }) {
                        Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212))
        ) {
            // --- WORKSPACE ---
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 200.dp), // Space for dock + trimmer
                contentAlignment = Alignment.Center
            ) {
                val workspaceSize = minOf(maxWidth, maxHeight) * 0.9f
                val density = LocalDensity.current
                
                val canvasSize = 512f
                val scaleFromCanvas = remember(workspaceSize) { with(density) { workspaceSize.toPx() } / canvasSize }
                val scaleToCanvas = remember(workspaceSize) { canvasSize / with(density) { workspaceSize.toPx() } }
                val textFontSize = remember(workspaceSize) { with(density) { (32f * scaleFromCanvas).toSp() } }

                // 512x512 Container (Mask)
                Box(
                    modifier = Modifier
                        .size(workspaceSize)
                        .aspectRatio(1f)
                        .background(Color.Black) // Background for video
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures { viewModel.onTextSelected(null) }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                viewModel.updateCropState(offset = pan, scale = zoom)
                            }
                        }
                ) {
                    // Video Player Layer
                    if (videoUri != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = viewModel.player
                                    useController = false // Hide default controls
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Scale to fill
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = cropState.scale
                                    scaleY = cropState.scale
                                    translationX = cropState.offset.x
                                    translationY = cropState.offset.y
                                }
                        )
                    }

                    // Text Overlay Layer
                    texts.forEach { textData ->
                        key(textData.id) {
                            val isSelected = textData.id == selectedTextId
                            val borderModifier = if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            } else { Modifier }

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
                                        detectTapGestures { viewModel.onTextSelected(textData.id) }
                                    }
                                    .pointerInput(textData.id, isSelected) {
                                        if (isSelected) {
                                            detectTransformGestures { _, pan, zoom, rotation ->
                                                val canvasPan = pan * scaleToCanvas
                                                viewModel.updateSelectedText(offset = canvasPan, scale = zoom, rotation = rotation)
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
                    
                    // Grid Overlay
                    if (showGrid) {
                        GridOverlay(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            // --- BOTTOM DOCK ---
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    // Trimmer Control with Timeline
                    if (videoDuration > 0) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.video_editor_trim_video) + ": ${formatTime(trimRange.start)} - ${formatTime(trimRange.endInclusive)}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Timeline + Slider Stack
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background Filmstrip
                                if (thumbnails.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        thumbnails.forEach { bitmap ->
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    }
                                }
                                
                                // Slider Overlay
                                RangeSlider(
                                    value = trimRange.start.toFloat()..trimRange.endInclusive.toFloat(),
                                    onValueChange = { range ->
                                        viewModel.updateTrimRange(range.start.toLong(), range.endInclusive.toLong())
                                    },
                                    valueRange = 0f..videoDuration.toFloat(),
                                    steps = 0,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Tools / Text Editor
                    val selectedText = texts.find { it.id == selectedTextId }
                    
                    AnimatedVisibility(visible = selectedText != null) {
                        if (selectedText != null) {
                            TextEditorPanel(
                                textData = selectedText,
                                onFontChange = { viewModel.updateSelectedTextFont(it) },
                                onColorChange = { viewModel.updateSelectedTextColor(it) },
                                onScaleChange = { viewModel.setTextScale(it) },
                                onDone = { viewModel.onTextSelected(null) }
                            )
                        }
                    }

                    AnimatedVisibility(visible = selectedText == null) {
                        VideoToolsPanel(
                            isGridEnabled = showGrid,
                            onAddText = { viewModel.showTextDialog(true) },
                            onToggleGrid = { viewModel.toggleGrid(!showGrid) }
                        )
                    }
                }
            }

            // Loading Overlay
            if (isBusy) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(processingMessage ?: stringResource(R.string.loading), color = Color.White)
                        
                        // Cancel Button
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.cancelSave() }) {
                            Text(stringResource(R.string.video_editor_cancel_button), color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoToolsPanel(
    isGridEnabled: Boolean,
    onAddText: () -> Unit,
    onToggleGrid: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolButton(
            icon = Icons.Default.TextFields,
            label = stringResource(R.string.add_text),
            onClick = onAddText
        )
        
        ToolButton(
            icon = if (isGridEnabled) Icons.Default.Grid3x3 else Icons.Default.GridOff,
            label = stringResource(R.string.video_editor_grid),
            isActive = isGridEnabled,
            onClick = onToggleGrid
        )
    }
}

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        val color = Color.White.copy(alpha = 0.5f)
        
        // Vertical
        drawLine(color, Offset(w/3, 0f), Offset(w/3, h), stroke)
        drawLine(color, Offset(2*w/3, 0f), Offset(2*w/3, h), stroke)
        
        // Horizontal
        drawLine(color, Offset(0f, h/3), Offset(w, h/3), stroke)
        drawLine(color, Offset(0f, 2*h/3), Offset(w, 2*h/3), stroke)
        
        // Border
        drawRect(color, style = Stroke(stroke))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
