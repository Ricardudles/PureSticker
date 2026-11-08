package com.example.wppsticker.ui.videoeditor

import android.graphics.Bitmap
import android.content.pm.ApplicationInfo
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
import com.example.wppsticker.ui.editor.TextData
import com.example.wppsticker.ui.components.StickerCanvasBoundary
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    navController: NavController,
    viewModel: VideoEditorViewModel = hiltViewModel()
) {
    val videoUri by viewModel.videoUri.collectAsState()
    val isGif by viewModel.isGif.collectAsState()
    val exportProfile by viewModel.exportProfile.collectAsState()
    val cropState by viewModel.cropState.collectAsState()
    val trimRange by viewModel.trimRange.collectAsState()
    val videoDuration by viewModel.videoDuration.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    
    val texts by viewModel.texts.collectAsState()
    val selectedTextId by viewModel.selectedTextId.collectAsState()
    val showTextDialog by viewModel.showTextDialog.collectAsState()
    
    val isBusy by viewModel.isBusy.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportFailure by viewModel.exportFailure.collectAsState()
    val navigateToSave by viewModel.navigateToSave.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()

    // Undo/Redo States
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isDebuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    var showExitDialog by remember { mutableStateOf(false) }
    
    // Retrieve packageId to forward it
    val packageIdArg = remember { 
        navController.currentBackStackEntry?.arguments?.getInt("packageId", -1) ?: -1
    }

    // Intercept Back Button
    BackHandler(enabled = !isBusy) { // Disable back handler when busy to prevent exit dialog
        showExitDialog = true
    }
    // Additional BackHandler to consume back press when busy (doing nothing)
    BackHandler(enabled = isBusy) { }

    LaunchedEffect(navigateToSave) {
        navigateToSave?.let { route ->
            var finalRoute = if (route.contains("isAnimated=")) route else "$route&isAnimated=true"
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
        val focusRequester = remember { FocusRequester() }
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        
        AlertDialog(
            onDismissRequest = { viewModel.showTextDialog(false) },
            title = { Text(stringResource(R.string.new_text_title)) },
            text = { 
                OutlinedTextField(
                    value = textInput, 
                    onValueChange = { textInput = it },
                    placeholder = { Text(stringResource(R.string.type_here_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { 
                        if (textInput.isNotBlank()) viewModel.addText(textInput) 
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
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

    exportFailure?.let { failure ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportFailure() },
            title = { Text(stringResource(R.string.animated_export_error_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(failure.message)
                    failure.debugDetails?.let { details ->
                        Text(
                            stringResource(R.string.animated_export_error_debug_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SelectionContainer {
                            Text(
                                details,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissExportFailure() }) {
                    Text(stringResource(R.string.animated_export_dismiss))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { if (!isBusy) showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    // Undo Button
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo && !isBusy) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.undo),
                            tint = if (canUndo && !isBusy) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    // Redo Button
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo && !isBusy) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.redo),
                            tint = if (canRedo && !isBusy) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = { viewModel.onSave() }, enabled = !isBusy) {
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
                .background(MaterialTheme.colorScheme.background)
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
                        .background(MaterialTheme.colorScheme.surface) // Background for video
                        .clipToBounds()
                        .pointerInput(Unit) {
                            if (!isBusy) detectTapGestures { viewModel.onTextSelected(null) }
                        }
                        .pointerInput(Unit) {
                            if (!isBusy) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    if (pan != Offset.Zero || zoom != 1f || rotation != 0f) {
                                        viewModel.onGestureStart()
                                        viewModel.updateCropState(
                                            offset = Offset(pan.x * scaleToCanvas, pan.y * scaleToCanvas),
                                            scale = zoom,
                                            rotation = rotation
                                        )
                                    } else {
                                        viewModel.onGestureEnd()
                                    }
                                }
                            }
                        }
                ) {
                    // Checkerboard Background (Transparency Indicator)
                    val checkerColor1 = MaterialTheme.colorScheme.outlineVariant
                    val checkerColor2 = MaterialTheme.colorScheme.surface
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val checkerSize = 20.dp.toPx() // Checker size
                        val rows = (size.height / checkerSize).toInt() + 1
                        val cols = (size.width / checkerSize).toInt() + 1
                        
                        for (row in 0 until rows) {
                            for (col in 0 until cols) {
                                val color = if ((row + col) % 2 == 0) checkerColor1 else checkerColor2
                                drawRect(
                                    color = color,
                                    topLeft = Offset(col * checkerSize, row * checkerSize),
                                    size = Size(checkerSize, checkerSize)
                                )
                            }
                        }
                    }

                    // Video Player Layer
                    if (videoUri != null && isGif) {
                        GifTrimPreview(
                            uri = videoUri!!,
                            trimRange = trimRange,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = cropState.scale
                                    scaleY = cropState.scale
                                    rotationZ = cropState.rotation
                                    translationX = cropState.offset.x * scaleFromCanvas
                                    translationY = cropState.offset.y * scaleFromCanvas
                                }
                        )
                    } else if (videoUri != null) {
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
                                    rotationZ = cropState.rotation
                                    translationX = cropState.offset.x * scaleFromCanvas
                                    translationY = cropState.offset.y * scaleFromCanvas
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
                                        if (!isBusy) detectTapGestures { viewModel.onTextSelected(textData.id) }
                                    }
                                    .pointerInput(textData.id, isSelected) {
                                        if (isSelected && !isBusy) {
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

                    // Keep the sticker boundary visible above the video, text and optional grid.
                    StickerCanvasBoundary()
                }
            }

            // --- BOTTOM DOCK ---
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding() // Safe Area
                        .padding(16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    // Trimmer Control with Timeline
                    if (videoDuration > 0) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.video_editor_trim_selection,
                                    formatTime(trimRange.first),
                                    formatTime(trimRange.last),
                                    formatDuration(trimRange.last - trimRange.first)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = stringResource(R.string.video_editor_quick_durations),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(1_000L, 3_000L, 5_000L, 10_000L)
                                    .filter { it <= videoDuration }
                                    .forEach { durationMs ->
                                        val selected = trimRange.last - trimRange.first == durationMs
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = { viewModel.setTrimDuration(durationMs) }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.video_editor_seconds_short, durationMs / 1_000),
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                            }
                            
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
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                    value = trimRange.first.toFloat()..trimRange.last.toFloat(),
                                    onValueChange = { range ->
                                        viewModel.updateTrimRange(range.start.toLong(), range.endInclusive.toLong())
                                    },
                                    onValueChangeFinished = viewModel::onTrimChangeFinished,
                                    valueRange = 0f..videoDuration.toFloat(),
                                    steps = 0,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimatedExportProfile.entries.forEach { profile ->
                            val label = when (profile) {
                                AnimatedExportProfile.FLUID -> stringResource(R.string.animated_profile_fluid)
                                AnimatedExportProfile.BALANCED -> stringResource(R.string.animated_profile_balanced)
                                AnimatedExportProfile.SHARP -> stringResource(R.string.animated_profile_sharp)
                            }
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setExportProfile(profile) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (profile == exportProfile) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (profile == exportProfile) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall
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
                                onDelete = { viewModel.deleteSelectedText() },
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
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .widthIn(max = 460.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 18.dp
                    ) {
                        val selectedClipDurationMs = (trimRange.last - trimRange.first).coerceAtLeast(0L)
                        val progressFraction = if (exportProgress.totalFrames > 0) {
                            (exportProgress.frame.toFloat() / exportProgress.totalFrames).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        val progressPercent = progressFraction?.let { (it * 100).toInt() }
                        val reportedSize = exportProgress.candidateSizeBytes.takeIf { it > 0L }
                            ?: exportProgress.previousCandidateSizeBytes
                        val isLastCandidateOversized = reportedSize > AnimatedStickerExporter.MAX_BYTES
                        val profileLabel = when (exportProfile) {
                            AnimatedExportProfile.FLUID -> stringResource(R.string.animated_profile_fluid)
                            AnimatedExportProfile.BALANCED -> stringResource(R.string.animated_profile_balanced)
                            AnimatedExportProfile.SHARP -> stringResource(R.string.animated_profile_sharp)
                        }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 640.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (progressFraction == null) {
                                    CircularProgressIndicator(modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
                                } else {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp)) {
                                        CircularProgressIndicator(
                                            progress = { progressFraction },
                                            modifier = Modifier.fillMaxSize(),
                                            strokeWidth = 3.dp
                                        )
                                        Text("${progressPercent}%", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        stringResource(R.string.animated_export_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (exportProgress.stage) {
                                            AnimatedExportStage.PREPARING -> stringResource(R.string.animated_export_preparing)
                                            AnimatedExportStage.DECODING -> stringResource(R.string.animated_export_decoding)
                                            AnimatedExportStage.ENCODING -> stringResource(R.string.animated_export_encoding)
                                            AnimatedExportStage.VALIDATING -> stringResource(R.string.animated_export_validating)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (progressFraction != null) {
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(
                                        R.string.animated_export_frames,
                                        exportProgress.frame,
                                        exportProgress.totalFrames,
                                        progressPercent ?: 0
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            if (exportProgress.totalAttempts > 0) {
                                Text(
                                    stringResource(R.string.animated_export_profile, profileLabel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExportMetric(
                                        label = stringResource(R.string.animated_export_pass_label),
                                        value = "${exportProgress.attempt}/${exportProgress.totalAttempts}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ExportMetric(
                                        label = stringResource(R.string.animated_export_fps_label),
                                        value = stringResource(R.string.animated_export_fps_value, exportProgress.frameRate),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ExportMetric(
                                        label = stringResource(R.string.animated_export_quality_label),
                                        value = "${exportProgress.quality}/100",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.animated_export_clip_progress,
                                            formatPreciseTime(exportProgress.sourceTimeMs.coerceAtMost(selectedClipDurationMs)),
                                            formatPreciseTime(selectedClipDurationMs)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        stringResource(R.string.animated_export_clip_explanation),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (reportedSize > 0L) {
                                        Text(
                                            stringResource(
                                                if (isLastCandidateOversized) R.string.animated_export_measured_size_over
                                                else R.string.animated_export_measured_size,
                                                formatFileSize(reportedSize),
                                                formatFileSize(AnimatedStickerExporter.MAX_BYTES)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLastCandidateOversized) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                stringResource(R.string.animated_export_elapsed, formatTime(exportProgress.elapsedMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isDebuggable) {
                                Text(
                                    stringResource(R.string.animated_export_debug_mode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            TextButton(
                                onClick = { viewModel.cancelSave() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    stringResource(R.string.animated_export_cancel),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GifTrimPreview(uri: android.net.Uri, trimRange: LongRange, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var frame by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val latestRange by rememberUpdatedState(trimRange)
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            AnimatedStickerExporter.GifSource(context, uri).use { source ->
                val step = (1000f / source.frameRate).toLong().coerceAtLeast(8L)
                var position = latestRange.first
                while (isActive) {
                    val range = latestRange
                    if (position < range.first || position >= range.last) position = range.first
                    val nextFrame = source.frameAt(position)
                    withContext(Dispatchers.Main) {
                        val previous = frame
                        frame = nextFrame
                        if (previous !== nextFrame) previous?.recycle()
                    }
                    position += step
                    if (position >= latestRange.last) position = latestRange.first
                    delay(step)
                }
            }
        }
    }
    DisposableEffect(uri) {
        onDispose { frame?.recycle(); frame = null }
    }
    frame?.let { bitmap ->
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@Composable
private fun ExportMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

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

private fun formatPreciseTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = safeMs % 1000
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, milliseconds)
}

private fun formatFileSize(bytes: Long): String = String.format(Locale.US, "%.1f KB", bytes / 1024f)

private fun formatDuration(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000f)
