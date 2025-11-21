package com.example.wppsticker.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                Toast.makeText(context, context.getString(R.string.whatsapp_not_found), Toast.LENGTH_LONG).show()
                viewModel.onSendIntentLaunched()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, // Use Dark Background
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.name) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { permissionState.launchPermissionRequest() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_sticker))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = stickerPackagesState) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                         EmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
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
                }
                is UiState.Empty -> EmptyState()
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground), // Fallback icon
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(100.dp).alpha(0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.no_packs_found), color = Color.Gray, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tap_plus_create), color = Color.DarkGray)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- Header Row ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Info (Name & Author)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stickerPackage.stickerPackage.name, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.stickers_by_author, stickerPackage.stickers.size, stickerPackage.stickerPackage.author), 
                        style = MaterialTheme.typography.bodySmall, 
                        color = Color.Gray
                    )
                }

                // Actions
                IconButton(onClick = onSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, 
                        contentDescription = stringResource(R.string.send_to_whatsapp_desc),
                        tint = MaterialTheme.colorScheme.secondary // Green-ish accent
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = stringResource(R.string.delete_pack_desc),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Preview Grid ---
            if (stickerPackage.stickers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val maxPreview = 6
                    val stickersToShow = stickerPackage.stickers.take(maxPreview)
                    val remainingCount = stickerPackage.stickers.size - maxPreview

                    stickersToShow.forEachIndexed { index, sticker ->
                        val isLastInPreview = index == stickersToShow.lastIndex
                        val hasMore = remainingCount > 0

                        if (isLastInPreview && hasMore) {
                            // Last item with overlay
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(context.filesDir, sticker.imageFile))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stringResource(R.string.more_stickers_desc),
                                    modifier = Modifier.fillMaxSize(),
                                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.6f), blendMode = BlendMode.SrcOver)
                                )
                                Text(
                                    text = stringResource(R.string.remaining_stickers_count, remainingCount + 1),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Regular sticker image
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(context.filesDir, sticker.imageFile))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.empty_pack), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
