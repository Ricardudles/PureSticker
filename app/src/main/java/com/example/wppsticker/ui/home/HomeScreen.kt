package com.example.wppsticker.ui.home

import android.content.ActivityNotFoundException
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wppsticker.R
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.ui.components.CreatePackageDialog
import com.example.wppsticker.ui.components.StickerPackageCard
import com.example.wppsticker.util.UiState
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "StickerAppDebug"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val stickerPackagesState by viewModel.stickerPackages.collectAsState()
    val sendIntent by viewModel.sendIntent.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<StickerPackage?>(null) }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collectLatest { event ->
            Toast.makeText(context, event, Toast.LENGTH_SHORT).show()
        }
    }

    val whatsappLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { 
        Log.d(TAG, "HomeScreen: WhatsApp Activity returned.")
        viewModel.onSendIntentLaunched()
    }

    val whatsappNotFoundMessage = stringResource(R.string.whatsapp_not_found)
    LaunchedEffect(sendIntent) {
        sendIntent?.let { intent ->
            try {
                whatsappLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Could not launch implicit intent", e)
                Toast.makeText(context, whatsappNotFoundMessage, Toast.LENGTH_LONG).show()
                viewModel.onSendIntentLaunched()
            }
        }
    }

    if (showCreateDialog) {
        CreatePackageDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, author, isAnimated, email, website, privacy, license ->
                viewModel.createStickerPackage(name, author, isAnimated, email, website, privacy, license) { newPackId ->
                    showCreateDialog = false
                    // Optionally navigate to the new pack
                    navController.navigate("${Screen.StickerPack.name}/$newPackId")
                }
            }
        )
    }
    
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_package_title)) },
            text = { Text(stringResource(R.string.delete_package_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog?.let { viewModel.deleteStickerPackage(it.id) }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
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
                onClick = { navController.navigate(Screen.StickerTypeSelection.name) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_new_pack))
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
                                StickerPackageCard(
                                    name = stickerPackage.stickerPackage.name,
                                    author = stickerPackage.stickerPackage.author,
                                    isAnimated = stickerPackage.stickerPackage.animated,
                                    stickers = stickerPackage.stickers,
                                    onDelete = { showDeleteDialog = stickerPackage.stickerPackage },
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
