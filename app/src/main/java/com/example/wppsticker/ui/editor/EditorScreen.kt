package com.example.wppsticker.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.wppsticker.R

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
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept Back Button
    BackHandler(enabled = !isBusy) { // Disable back handler when busy to prevent exit dialog
        showExitDialog = true
    }
    // Additional BackHandler to consume back press when busy (doing nothing)
    BackHandler(enabled = isBusy) { }

    LaunchedEffect(navigateToSave) {
        navigateToSave?.let { route ->
            navController.navigate(route)
            viewModel.onNavigatedToSave()
        }
    }

    // --- Dialogs ---
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
                Button(
                    onClick = { viewModel.addText(textInput) },
                    enabled = textInput.isNotBlank()
                ) { Text(stringResource(R.string.add)) } 
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
                    IconButton(onClick = { if (!isBusy) showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    // Undo Button
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo && !isBusy) {
                         Icon(
                             Icons.AutoMirrored.Filled.Undo, 
                             contentDescription = "Undo",
                             tint = if (canUndo && !isBusy) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                         )
                    }
                    
                    // Redo Button
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo && !isBusy) {
                         Icon(
                             Icons.AutoMirrored.Filled.Redo, 
                             contentDescription = "Redo",
                             tint = if (canRedo && !isBusy) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                         )
                    }
                    
                    // "Next" Button
                    TextButton(onClick = { viewModel.onSaveAndContinue() }, enabled = !isBusy) {
                        Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background) // Use Theme Background
        ) {
            // --- WORKSPACE (TOP, takes remaining space) ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f) // Takes all available space above the dock
                    .fillMaxWidth()
                    .animateContentSize(), // Smooth transition if dock height changes
                contentAlignment = Alignment.Center
            ) {
                // Use maxWidth/maxHeight directly.
                @Suppress("UnusedBoxWithConstraintsScope")
                val workspaceSize = minOf(maxWidth, maxHeight) * 0.9f // Add some breathing room
                val density = LocalDensity.current
                
                val canvasSize = 512f
                val scaleToCanvas = remember(workspaceSize) { canvasSize / with(density) { workspaceSize.toPx() } }
                val scaleFromCanvas = remember(workspaceSize) { with(density) { workspaceSize.toPx() } / canvasSize }
                val textFontSize = remember(workspaceSize) {
                    with(density) { (32f * scaleFromCanvas).toSp() }
                }

                // The 512x512 Representation
                Box(
                    modifier = Modifier
                        .size(workspaceSize)
                        .aspectRatio(1f)
                        .background(Color.Transparent)
                        .clipToBounds() 
                ) {
                    // Checkerboard Background (Transparency Indicator)
                    val checkerColor1 = MaterialTheme.colorScheme.outline
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

                    // Guide Border
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.3f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                            )
                        )
                    }

                    // Image Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                if (!isBusy) detectTapGestures { viewModel.onTextSelected(null) }
                            }
                            .pointerInput(Unit) {
                                if (!isBusy) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        // Signal start of gesture (will only trigger undo save once per sequence)
                                        viewModel.onGestureStart()
                                        
                                        if (viewModel.selectedTextId.value == null) {
                                            val canvasPan = pan * scaleToCanvas
                                            viewModel.updateImageState(offset = canvasPan, scale = zoom, rotation = rotation)
                                        }
                                    }
                                }
                            }
                    ) {
                        imageUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
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

                    // Text Layer
                    texts.forEach { textData ->
                        key(textData.id) {
                            val isSelected = textData.id == selectedTextId
                            val borderModifier = if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
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
                                        if (!isBusy) detectTapGestures { viewModel.onTextSelected(textData.id) }
                                    }
                                    .pointerInput(textData.id, isSelected) {
                                        if (isSelected && !isBusy) {
                                            detectTransformGestures { _, pan, zoom, rotation ->
                                                viewModel.onGestureStart()
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
                }
            }

            // --- BOTTOM DOCK ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface, // Use Theme Surface
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding() // Use Safe Area
                        .padding(16.dp)
                        .padding(bottom = 8.dp) 
                ) {
                    val selectedText = texts.find { it.id == selectedTextId }

                    AnimatedVisibility(
                        visible = selectedText != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
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

                    AnimatedVisibility(
                        visible = selectedText == null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        MainToolsPanel(
                            isSnapEnabled = isSnapEnabled,
                            snapStrength = snapStrength,
                            onAddText = { viewModel.showTextDialog(true) },
                            onToggleSnap = { viewModel.toggleSnap(it) },
                            onChangeSnapStrength = { viewModel.setSnapStrength(it) },
                            onRemoveBackground = { viewModel.removeBackground() }
                        )
                    }
                }
            }
        }
            
        if (isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(enabled = true, onClick = {}), // Block interactions
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MainToolsPanel(
    isSnapEnabled: Boolean,
    snapStrength: Int,
    onAddText: () -> Unit,
    onToggleSnap: (Boolean) -> Unit,
    onChangeSnapStrength: (Int) -> Unit,
    onRemoveBackground: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        
        // Magnet Strength Indicator (Only if enabled)
        AnimatedVisibility(
            visible = isSnapEnabled,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.magnet_strength, snapStrength),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { level ->
                        val isSelected = level <= snapStrength
                        val isCurrent = level == snapStrength
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 16.dp else 12.dp) // Highlight current
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onChangeSnapStrength(level) }
                        )
                    }
                }
            }
        }

        // Main Buttons Row
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
                icon = Icons.Default.AutoFixHigh,
                label = stringResource(R.string.remove_background),
                onClick = onRemoveBackground
            )
            
            ToolButton(
                icon = if (isSnapEnabled) Icons.Default.Grid3x3 else Icons.Default.GridOff,
                label = stringResource(R.string.magnet),
                isActive = isSnapEnabled,
                onClick = { onToggleSnap(!isSnapEnabled) }
            )
        }
    }
}

@Composable
fun TextEditorPanel(
    textData: TextData,
    onFontChange: (Int) -> Unit,
    onColorChange: (Color) -> Unit,
    onScaleChange: (Float) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    Column {
        // Header with Done Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.customize_text), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Delete Button
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }

                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.done))
                }
            }
        }

        // Font Selector
        Text(stringResource(R.string.font_style), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(0, 1, 2, 3, 4)) { index ->
                FontSelectorItem(
                    index = index, 
                    isSelected = textData.fontIndex == index,
                    onClick = { onFontChange(index) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Size Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Slider(
                value = textData.scale,
                onValueChange = onScaleChange,
                valueRange = 0.5f..5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Color Selector
        ColorSelector(
            selectedColor = textData.color,
            onColorSelected = onColorChange
        )
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        lineHeight = fontSize
    )
}

@Composable
fun FontSelectorItem(index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val label = when(index) {
        0 -> stringResource(R.string.font_default)
        1 -> stringResource(R.string.font_serif)
        2 -> stringResource(R.string.font_mono)
        3 -> stringResource(R.string.font_cursive)
        4 -> stringResource(R.string.font_bold)
        else -> "?"
    }
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp)) // Softer corners
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = textColor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ColorSelector(selectedColor: Color, onColorSelected: (Color) -> Unit, modifier: Modifier = Modifier) {
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(colors) { color ->
            val isSelected = color == selectedColor
            val borderModifier = if (isSelected) {
                Modifier.border(2.dp, Color.White, CircleShape)
            } else {
                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(borderModifier)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}