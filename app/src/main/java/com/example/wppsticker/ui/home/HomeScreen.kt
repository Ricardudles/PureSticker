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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wppsticker.R
import com.example.wppsticker.data.local.StickerPackageWithStickers
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.util.UiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "StickerAppDebug"

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val stickerPackagesState by viewModel.stickerPackages.collectAsState()
    val sendIntent by viewModel.sendIntent.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collectLatest { event ->
            Toast.makeText(context, event, Toast.LENGTH_SHORT).show()
        }
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
            try {
                whatsappLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Could not launch implicit intent", e)
                Toast.makeText(context, "WhatsApp not found.", Toast.LENGTH_LONG).show()
                viewModel.onSendIntentLaunched()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.name) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings & Backup")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                permissionState.launchPermissionRequest()
             }) {
                Icon(Icons.Default.Add, contentDescription = "Create Sticker")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = stickerPackagesState) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.data) { stickerPackage ->
                            StickerPackageItem(
                                stickerPackage = stickerPackage,
                                onDelete = { viewModel.deleteStickerPackage(stickerPackage.stickerPackage.id) },
                                onSend = { viewModel.sendStickerPack(stickerPackage.stickerPackage.id) },
                                onClick = {
                                    navController.navigate("${Screen.StickerPack.name}/${stickerPackage.stickerPackage.id}")
                                }
                            )
                        }
                    }
                }
                is UiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No sticker packs yet. Tap '+' to create one!")
                    }
                }
            }
        }
    }
}


@Composable
private fun StickerPackageItem(
    stickerPackage: StickerPackageWithStickers,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val trayIconFile = File(context.filesDir, stickerPackage.stickerPackage.trayImageFile)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // --- Header --- 
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(trayIconFile)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Tray Icon",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stickerPackage.stickerPackage.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(stickerPackage.stickerPackage.author, fontSize = 14.sp, color = Color.Gray)
                }
                // Actions
                IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to WhatsApp")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Package")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Sticker Preview Row ---
            if (stickerPackage.stickers.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stickerPackage.stickers.take(5)) { sticker ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(context.filesDir, sticker.imageFile))
                                .crossfade(true)
                                .size(256) // Load a smaller version
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    if (stickerPackage.stickers.size > 5) {
                        item { // Wrapped in item scope
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${stickerPackage.stickers.size - 5}",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
