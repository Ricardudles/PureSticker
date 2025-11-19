package com.example.wppsticker.ui.stickerpack

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.util.UiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
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

    Scaffold(
        topBar = {
            val title = if (stickerPackageState is UiState.Success) {
                (stickerPackageState as UiState.Success).data.stickerPackage.name
            } else {
                ""
            }
            TopAppBar(title = { Text(title) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                 permissionState.launchPermissionRequest()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Sticker")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val state = stickerPackageState) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.data.stickerPackage.trayImageFile.isNotEmpty()) {
                            TrayIconInfo(file = File(LocalContext.current.filesDir, state.data.stickerPackage.trayImageFile))
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.data.stickers) { sticker ->
                                StickerItem(
                                    sticker = sticker,
                                    onDelete = { viewModel.deleteSticker(sticker.id) }
                                )
                            }
                        }
                    }
                }
                is UiState.Empty -> {
                    Text("No stickers in this pack. Add one!")
                }
            }
        }
    }
}

@Composable
private fun TrayIconInfo(file: File) {
    val sizeInKb = file.length() / 1024
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    val width = options.outWidth
    val height = options.outHeight

    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
        Text("Tray Icon: ", fontWeight = FontWeight.Bold)
        Text("$width x $height, $sizeInKb KB", color = if (width == 96 && height == 96 && sizeInKb <= 50) Color.Green else Color.Red)
    }
}


@Composable
private fun StickerItem(
    sticker: Sticker,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sizeColor = if (sticker.width == 512 && sticker.height == 512 && sticker.sizeInKb <= 100) Color.Green else Color.Red

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(
                model = File(context.filesDir, sticker.imageFile),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Sticker")
            }
        }
        Text("${sticker.width}x${sticker.height}, ${sticker.sizeInKb} KB", fontSize = 10.sp, color = sizeColor)
    }
}
