package com.example.wppsticker.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.wppsticker.R
import com.example.wppsticker.nav.Screen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StickerTypeSelectionScreen(navController: NavController) {
    val context = LocalContext.current

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var isAnimatedSelection by remember { mutableStateOf(false) }

    if (pickedUri != null) {
        val uriToProcess = pickedUri!!
        LaunchedEffect(uriToProcess) {
            val encodedUri = URLEncoder.encode(uriToProcess.toString(), StandardCharsets.UTF_8.toString())
            val route = if (isAnimatedSelection) {
                "${Screen.VideoEditor.name}/$encodedUri"
            } else {
                "${Screen.Editor.name}/$encodedUri"
            }
            navController.navigate(route)
            pickedUri = null
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isAnimatedSelection = false
            pickedUri = uri
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isAnimatedSelection = true
            pickedUri = uri
        }
    }

    val imagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    var pendingPermissionRequest by remember { mutableStateOf<String?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPermissionSettings by remember { mutableStateOf(false) }

    val imagePermissionState = rememberPermissionState(imagePermission) { isGranted ->
        if (isGranted) imageLauncher.launch("image/*")
        else if (!isGranted) showPermissionSettings = true
    }

    val videoPermissionState = rememberPermissionState(videoPermission) { isGranted ->
        if (isGranted) videoLauncher.launch("video/*")
        else if (!isGranted) showPermissionSettings = true
    }

    fun onTypeSelected(isAnimated: Boolean) {
        val state = if (isAnimated) videoPermissionState else imagePermissionState
        if (state.status.isGranted) {
            if (isAnimated) videoLauncher.launch("video/*") else imageLauncher.launch("image/*")
        } else if (state.status.shouldShowRationale) {
            pendingPermissionRequest = if (isAnimated) "video" else "image"
            showPermissionRationale = true
        } else {
            state.launchPermissionRequest()
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.permission_required_title)) },
            text = { Text(stringResource(R.string.permission_required_rationale)) },
            confirmButton = {
                Button(onClick = {
                    showPermissionRationale = false
                    if (pendingPermissionRequest == "video") videoPermissionState.launchPermissionRequest()
                    else imagePermissionState.launchPermissionRequest()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showPermissionRationale = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showPermissionSettings) {
        AlertDialog(
            onDismissRequest = { showPermissionSettings = false },
            title = { Text(stringResource(R.string.permission_required_title)) },
            text = { Text(stringResource(R.string.permission_permanently_denied_message)) },
            confirmButton = {
                Button(onClick = {
                    showPermissionSettings = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton(onClick = { showPermissionSettings = false }) { Text(stringResource(R.string.not_now)) } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_new_pack), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.select_sticker_type),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TypeSelectionCard(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.static_sticker),
                    subtitle = stringResource(R.string.static_sticker_desc),
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f),
                    onClick = { onTypeSelected(false) }
                )

                TypeSelectionCard(
                    icon = Icons.Default.Movie,
                    title = stringResource(R.string.animated_sticker),
                    subtitle = stringResource(R.string.coming_soon),
                    color = Color.Gray,
                    modifier = Modifier.weight(1f),
                    isEnabled = false,
                    onClick = { /* onTypeSelected(true) */ }
                )
            }
        }
    }
}

@Composable
fun TypeSelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(0.8f)
            .clickable(enabled = isEnabled) { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2F2F2F)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = color
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color.White else Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEnabled) Color.LightGray else Color.Gray
            )
        }
    }
}
