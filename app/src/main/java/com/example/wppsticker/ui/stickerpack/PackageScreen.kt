package com.example.wppsticker.ui.stickerpack

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.util.UiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun PackageScreen(
    navController: NavController,
    viewModel: PackageViewModel = hiltViewModel()
) {
    val stickerPackageState by viewModel.stickerPackage.collectAsState()
    val sendIntent by viewModel.sendIntent.collectAsState()
    val context = LocalContext.current
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteStickerDialog by remember { mutableStateOf<Sticker?>(null) }
    
    // Selection State
    var selectedStickers by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode = selectedStickers.isNotEmpty()
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
                navController.navigate("${Screen.Editor.name}/$encodedUri")
            }
        }
    )

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        }
    }
    
    // Handle Back Press to clear selection
    BackHandler(enabled = isSelectionMode) {
        selectedStickers = emptySet()
    }

    // Effects for Toast and Intent
    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            Toast.makeText(context, event, Toast.LENGTH_LONG).show()
        }
    }

    val whatsappLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { 
        viewModel.onSendIntentLaunched()
    }

    LaunchedEffect(sendIntent) {
        sendIntent?.let { intent ->
            try {
                whatsappLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "WhatsApp not found.", Toast.LENGTH_LONG).show()
                viewModel.onSendIntentLaunched()
            }
        }
    }

    // Edit Dialog
    if (showEditDialog && stickerPackageState is UiState.Success) {
        val currentPackage = (stickerPackageState as UiState.Success).data.stickerPackage
        
        var editName by remember { mutableStateOf(currentPackage.name) }
        var editAuthor by remember { mutableStateOf(currentPackage.author) }
        var editEmail by remember { mutableStateOf(currentPackage.publisherEmail) }
        var editWebsite by remember { mutableStateOf(currentPackage.publisherWebsite) }
        var editPrivacyPolicy by remember { mutableStateOf(currentPackage.privacyPolicyWebsite) }
        var editLicense by remember { mutableStateOf(currentPackage.licenseAgreementWebsite) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Details") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = editAuthor,
                        onValueChange = { editAuthor = it },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = editWebsite,
                        onValueChange = { editWebsite = it },
                        label = { Text("Website") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = editPrivacyPolicy,
                        onValueChange = { editPrivacyPolicy = it },
                        label = { Text("Privacy Policy Website") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = editLicense,
                        onValueChange = { editLicense = it },
                        label = { Text("License Website") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updatePackageDetails(
                        name = editName,
                        author = editAuthor,
                        email = editEmail,
                        website = editWebsite,
                        privacyPolicy = editPrivacyPolicy,
                        license = editLicense
                    )
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Single Delete Confirmation Dialog
    if (showDeleteStickerDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteStickerDialog = null },
            title = { Text("Delete Sticker?") },
            text = { Text("Are you sure you want to remove this sticker from the pack? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteStickerDialog?.let { viewModel.deleteSticker(it.id) }
                        showDeleteStickerDialog = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStickerDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Batch Delete Confirmation Dialog
    if (showDeleteSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectionDialog = false },
            title = { Text("Delete ${selectedStickers.size} Stickers?") },
            text = { Text("Are you sure you want to remove the selected stickers? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStickers(selectedStickers.toList())
                        selectedStickers = emptySet()
                        showDeleteSelectionDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val title = if (isSelectionMode) {
                "${selectedStickers.size} Selected"
            } else if (stickerPackageState is UiState.Success) {
                (stickerPackageState as UiState.Success).data.stickerPackage.name
            } else {
                "Loading..."
            }
            
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                     if (isSelectionMode) {
                         IconButton(onClick = { selectedStickers = emptySet() }) {
                             Icon(Icons.Default.Close, contentDescription = "Close Selection")
                         }
                     } else {
                         IconButton(onClick = { navController.popBackStack() }) {
                             Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                         }
                     }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteSelectionDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    } else if (stickerPackageState is UiState.Success) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.navigationBarsPadding() // Ensures buttons are above the system gesture bar
                ) {
                    // 2. Add to WhatsApp Button (Green, Extended FAB, Floating Pill)
                    if (stickerPackageState is UiState.Success) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.sendStickerPack() },
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White,
                            expanded = true,
                            icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            text = { Text("Add to WhatsApp", fontWeight = FontWeight.Bold) }
                        )
                    }
                    
                    // 1. Add Sticker Button (Purple, Standard FAB)
                    FloatingActionButton(
                        onClick = { permissionState.launchPermissionRequest() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Sticker")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = stickerPackageState) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Success -> {
                    if (state.data.stickers.isEmpty()) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("This pack is empty.", color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap + to create a sticker", color = Color.DarkGray)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.data.stickers) { sticker ->
                                StickerItem(
                                    sticker = sticker,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedStickers.contains(sticker.id),
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            selectedStickers = selectedStickers + sticker.id
                                        }
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedStickers = if (selectedStickers.contains(sticker.id)) {
                                                selectedStickers - sticker.id
                                            } else {
                                                selectedStickers + sticker.id
                                            }
                                        }
                                        // Else normal click behavior (e.g. preview) if implemented
                                    },
                                    onDelete = { showDeleteStickerDialog = sticker }
                                )
                            }
                        }
                    }
                }
                is UiState.Empty -> {
                    Text("Pack not found", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerItem(
    sticker: Sticker,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(
                model = File(context.filesDir, sticker.imageFile),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp) 
            )
            
            if (isSelectionMode) {
                // Overlay for selection mode
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) 
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                // Delete Button Overlay (Only visible when NOT selecting)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
