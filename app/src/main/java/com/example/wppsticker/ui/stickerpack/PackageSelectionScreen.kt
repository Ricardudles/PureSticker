package com.example.wppsticker.ui.stickerpack

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.wppsticker.ui.components.EmptyStateFiltered
import com.example.wppsticker.ui.home.HomeViewModel
import com.example.wppsticker.ui.util.LoadingDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageSelectionScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = hiltViewModel(),
    saveStickerViewModel: SaveStickerViewModel = hiltViewModel()
) {
    val stickerPackagesState by homeViewModel.stickerPackages.collectAsState()
    val context = LocalContext.current
    
    // Get Arguments
    val isAnimatedArg = remember { 
        navController.currentBackStackEntry?.arguments?.getBoolean("isAnimated") ?: false 
    }
    val stickerUriArg = remember {
        navController.currentBackStackEntry?.arguments?.getString("stickerUri")
    }
    val emojisArg = remember {
        navController.currentBackStackEntry?.arguments?.getString("emojis")
    }
    val preSelectedPackageIdArg = remember {
        val id = navController.currentBackStackEntry?.arguments?.getInt("preSelectedPackageId", -1) ?: -1
        if (id == -1) null else id
    }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPackageId by remember { mutableStateOf<Int?>(preSelectedPackageIdArg) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    
    var showDuplicateDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDeleteDialog by remember { mutableStateOf<StickerPackage?>(null) }

    // Track if we should show the FAB (hidden when empty state is shown)
    var showFab by remember { mutableStateOf(false) }

    // Update selectedPackageId if preSelectedPackageIdArg changes (rare but safe)
    LaunchedEffect(preSelectedPackageIdArg) {
        if (preSelectedPackageIdArg != null) {
            selectedPackageId = preSelectedPackageIdArg
        }
    }

    // Observe save status and events
    val saveFinished by saveStickerViewModel.saveFinished.collectAsState()
    val loadingMessage by saveStickerViewModel.loadingMessage.collectAsState()
    
    LaunchedEffect(key1 = true) {
        saveStickerViewModel.events.collectLatest { event ->
            when(event) {
                is SaveStickerEvent.ShowDuplicateDialog -> showDuplicateDialog = event.onConfirm
                is SaveStickerEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    LaunchedEffect(saveFinished) {
        if (saveFinished) {
            showSuccessOverlay = true
            // Wait for animation
            delay(2000)
            showSuccessOverlay = false
            
             navController.popBackStack(Screen.Home.name, false)
             selectedPackageId?.let { pkgId ->
                 navController.navigate("${Screen.StickerPack.name}/$pkgId")
             }
        }
    }

    if (showCreateDialog) {
        CreatePackageDialog(
            onDismiss = { showCreateDialog = false },
            forceIsAnimated = isAnimatedArg,
            onCreate = { name, author, isAnimated, email, website, privacy, license ->
                homeViewModel.createStickerPackage(name, author, isAnimated, email, website, privacy, license) { newPackId ->
                    showCreateDialog = false
                    // Auto-select the newly created package
                    selectedPackageId = newPackId.toInt()
                    Toast.makeText(context, context.getString(R.string.package_created_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    if (loadingMessage != null) {
        LoadingDialog(message = loadingMessage!!)
    }
    
    if (showDuplicateDialog != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = null },
            title = { Text(stringResource(R.string.duplicate_sticker_title)) },
            text = { Text(stringResource(R.string.duplicate_sticker_msg)) },
            confirmButton = {
                Button(onClick = { showDuplicateDialog?.invoke(); showDuplicateDialog = null }) { Text(stringResource(R.string.add_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    
    // Delete Package Confirmation Dialog
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_package_title)) },
            text = { Text(stringResource(R.string.delete_package_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog?.let { homeViewModel.deleteStickerPackage(it.id) }
                        showDeleteDialog = null
                        // Reset selection if the deleted package was selected
                        if (selectedPackageId == showDeleteDialog?.id) {
                            selectedPackageId = null
                        }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isAnimatedArg) stringResource(R.string.select_anim_pack_title) 
                        else stringResource(R.string.select_static_pack_title), 
                        fontWeight = FontWeight.Bold
                    ) 
                }, 
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
        },
        floatingActionButton = {
            if (!showSuccessOverlay) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Confirm FAB (Visible only if selection)
                    if (selectedPackageId != null) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                when (val state = stickerPackagesState) {
                                    is com.example.wppsticker.util.UiState.Success -> {
                                        val pack = state.data.find { it.stickerPackage.id == selectedPackageId }?.stickerPackage
                                        if (pack != null) {
                                            if (stickerUriArg != null && emojisArg != null) {
                                                // Save Flow
                                                saveStickerViewModel.saveSticker(pack, emojisArg)
                                            } else {
                                                // Selection Flow (return result)
                                                navController.previousBackStackEntry
                                                    ?.savedStateHandle
                                                    ?.set("selected_package_id", pack.id)
                                                navController.popBackStack()
                                            }
                                        } else {
                                             Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    else -> {} // Should not happen if selectedPackageId is not null
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            text = { Text(stringResource(R.string.confirm_selection)) }
                        )
                    }

                    // Create FAB (Only visible if list is NOT empty, otherwise EmptyState button is used)
                    if (showFab) {
                        FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_new_pack))
                        }
                    }
                }
            }
        }
    ) { padding ->
         Box(modifier = Modifier.fillMaxSize().padding(padding)) {
             when (val state = stickerPackagesState) {
                is com.example.wppsticker.util.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is com.example.wppsticker.util.UiState.Success -> {
                    val filteredPacks = state.data.filter { it.stickerPackage.animated == isAnimatedArg }
                    
                    LaunchedEffect(filteredPacks.isEmpty()) {
                        showFab = filteredPacks.isNotEmpty()
                    }
                    
                    if (filteredPacks.isEmpty()) {
                         EmptyStateFiltered(isAnimated = isAnimatedArg, onCreateClick = { showCreateDialog = true })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredPacks) { stickerPackage ->
                                val pack = stickerPackage.stickerPackage
                                val stickers = stickerPackage.stickers
                                val count = stickers.size
                                val isFull = count >= 30
                                
                                StickerPackageCard(
                                    name = pack.name,
                                    author = pack.author,
                                    isAnimated = pack.animated,
                                    stickers = stickers,
                                    isFull = isFull,
                                    isSelected = selectedPackageId == pack.id,
                                    onDelete = { showDeleteDialog = pack },
                                    onClick = {
                                        if (!isFull) {
                                            // Toggle Selection
                                            selectedPackageId = if (selectedPackageId == pack.id) null else pack.id
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.pack_full_toast), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is com.example.wppsticker.util.UiState.Empty -> {
                    LaunchedEffect(Unit) { showFab = false }
                    EmptyStateFiltered(isAnimated = isAnimatedArg, onCreateClick = { showCreateDialog = true })
                }
            }
            
            // Success Overlay
            AnimatedVisibility(
                visible = showSuccessOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50), // Success Green
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.sticker_saved_success),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
         }
    }
}
