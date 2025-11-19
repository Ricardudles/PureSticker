package com.example.wppsticker.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wppsticker.R
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.util.UiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "StickerAppDebug"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val stickerPackagesState by viewModel.stickerPackages.collectAsState()
    val sendIntent by viewModel.sendIntent.collectAsState()
    val context = LocalContext.current
    var showDebugDialog by remember { mutableStateOf<StickerPackage?>(null) }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collectLatest { event ->
            Toast.makeText(context, event, Toast.LENGTH_SHORT).show()
        }
    }

    showDebugDialog?.let { pack ->
        AlertDialog(
            onDismissRequest = { showDebugDialog = null },
            title = { Text("Debug Info: ${pack.name}") },
            text = { 
                Column {
                    Text("ID: ${pack.id}", fontSize = 12.sp)
                    Text("Author: ${pack.author}", fontSize = 12.sp)
                    Text("Tray Icon File: ${pack.trayImageFile}", fontSize = 12.sp)
                }
             },
            confirmButton = { Button(onClick = { showDebugDialog = null }) { Text("OK") } }
        )
    }

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

    val whatsappLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { 
        Log.d(TAG, "HomeScreen: WhatsApp Activity returned.")
        viewModel.onSendIntentLaunched()
    }

    LaunchedEffect(sendIntent) {
        sendIntent?.let { intent ->
            Log.d(TAG, "HomeScreen: Received sendIntent. Launching WhatsApp... Package: ${intent.`package`}")
            try {
                whatsappLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "HomeScreen: First attempt failed for package ${intent.`package`}")
                
                // Fallback logic handled in UI because launch() throws here, not in ViewModel
                val currentPackage = intent.`package`
                
                if (currentPackage == null) {
                    // Implicit attempt failed. Try explicit com.whatsapp
                    Log.d(TAG, "HomeScreen: Implicit failed. Trying fallback to com.whatsapp")
                    val newIntent = Intent(intent).apply {
                        setPackage("com.whatsapp")
                    }
                    try {
                        whatsappLauncher.launch(newIntent)
                    } catch (e2: ActivityNotFoundException) {
                        // com.whatsapp failed. Try com.whatsapp.w4b
                        Log.d(TAG, "HomeScreen: com.whatsapp failed. Trying fallback to com.whatsapp.w4b")
                        val businessIntent = Intent(intent).apply {
                            setPackage("com.whatsapp.w4b")
                        }
                        try {
                            whatsappLauncher.launch(businessIntent)
                        } catch (e3: ActivityNotFoundException) {
                            Log.e(TAG, "HomeScreen: All attempts failed.")
                            Toast.makeText(context, "WhatsApp (Standard or Business) not found.", Toast.LENGTH_LONG).show()
                            viewModel.onSendIntentLaunched()
                        }
                    }
                } else if (currentPackage == "com.whatsapp") {
                    Log.d(TAG, "HomeScreen: Trying fallback to com.whatsapp.w4b")
                    val newIntent = Intent(intent).apply {
                        setPackage("com.whatsapp.w4b")
                    }
                    try {
                        whatsappLauncher.launch(newIntent)
                    } catch (e2: ActivityNotFoundException) {
                        Log.e(TAG, "HomeScreen: Fallback also failed.")
                        Toast.makeText(context, "WhatsApp (Standard or Business) not found.", Toast.LENGTH_LONG).show()
                        viewModel.onSendIntentLaunched()
                    }
                } else {
                    Log.e(TAG, "HomeScreen: Launch failed and no fallback available.")
                    Toast.makeText(context, "WhatsApp not installed or not found.", Toast.LENGTH_LONG).show()
                    viewModel.onSendIntentLaunched()
                }
            } catch (e: Exception) {
                Log.e(TAG, "HomeScreen: WhatsApp launch failed - Generic Error", e)
                Toast.makeText(context, "Error launching WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.onSendIntentLaunched()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.app_name)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                permissionState.launchPermissionRequest()
             }) {
                Icon(Icons.Default.Add, contentDescription = "Create Sticker")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val state = stickerPackagesState) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.data) { stickerPackage ->
                            StickerPackageItem(
                                stickerPackage = stickerPackage,
                                onDelete = { viewModel.deleteStickerPackage(stickerPackage.id) },
                                onSend = { viewModel.sendStickerPack(stickerPackage.id) },
                                onClick = {
                                    navController.navigate("${Screen.StickerPack.name}/${stickerPackage.id}")
                                },
                                onLongClick = { showDebugDialog = stickerPackage }
                            )
                        }
                    }
                }
                is UiState.Empty -> {
                    Text("No sticker packs found. Create one!")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerPackageItem(
    stickerPackage: StickerPackage,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongClick() },
                onTap = { onClick() }
            )
        }
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = stickerPackage.name)
                Text(text = stickerPackage.author)
            }
            Row {
                IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to WhatsApp")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Package")
                }
            }
        }
    }
}
