package com.example.wppsticker.ui.stickerpack

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wppsticker.R
import com.example.wppsticker.data.local.Sticker
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.ui.theme.WhatsAppGreen
import com.example.wppsticker.util.UiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
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
    val focusManager = LocalFocusManager.current
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteStickerDialog by remember { mutableStateOf<Sticker?>(null) }
    var showPreviewDialog by remember { mutableStateOf<Sticker?>(null) }

    // Selection State
    var selectedStickers by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode = selectedStickers.isNotEmpty()
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }

    val currentPackage = (stickerPackageState as? UiState.Success)?.data?.stickerPackage
    val currentPackageId = currentPackage?.id
    val isAnimated = currentPackage?.animated == true
    
    val videoEditorRouteName = Screen.VideoEditor.name
    val editorRouteName = Screen.Editor.name

    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            pickedUri = uri
        }
    )

    if (pickedUri != null) {
        val uriToProcess = pickedUri!!
        LaunchedEffect(uriToProcess) {
            val encodedUri = safeEncodeUri(uriToProcess.toString())
            val route = if (isAnimated && currentPackageId != null) {
                "$videoEditorRouteName/$encodedUri?packageId=$currentPackageId"
            } else {
                if (currentPackageId != null) {
                    "$editorRouteName/$encodedUri?packageId=$currentPackageId"
                } else {
                    "$editorRouteName/$encodedUri"
                }
            }
            navController.navigate(route)
            pickedUri = null
        }
    }

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    val permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (isAnimated) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var onPermissionResult: (Boolean) -> Unit = {}
    val permissionState = rememberPermissionState(permissionString) { isGranted ->
        onPermissionResult(isGranted)
    }

    onPermissionResult = { isGranted ->
        if (isGranted) {
            val mimeType = if (isAnimated) "video/*" else "image/*"
            imagePickerLauncher.launch(mimeType)
        } else {
            if (!permissionState.status.shouldShowRationale) {
                showPermissionSettingsDialog = true
            }
        }
    }
    
    BackHandler(enabled = isSelectionMode) {
        selectedStickers = emptySet()
    }

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

    val whatsappNotFoundMessage = stringResource(R.string.whatsapp_not_found)
    LaunchedEffect(sendIntent) {
        sendIntent?.let { intent ->
            try {
                whatsappLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, whatsappNotFoundMessage, Toast.LENGTH_LONG).show()
                viewModel.onSendIntentLaunched()
            }
        }
    }

    // Edit Dialog
    if (showEditDialog && stickerPackageState is UiState.Success) {
        val pack = (stickerPackageState as UiState.Success).data.stickerPackage
        
        var editName by remember { mutableStateOf(pack.name) }
        var editAuthor by remember { mutableStateOf(pack.author) }
        var editEmail by remember { mutableStateOf(pack.publisherEmail) }
        var editWebsite by remember { mutableStateOf(pack.publisherWebsite) }
        var editPrivacyPolicy by remember { mutableStateOf(pack.privacyPolicyWebsite) }
        var editLicense by remember { mutableStateOf(pack.licenseAgreementWebsite) }

        var showAdvanced by remember { mutableStateOf(false) }
        
        var nameError by remember { mutableStateOf(false) }
        var authorError by remember { mutableStateOf(false) }
        var emailError by remember { mutableStateOf(false) }
        var websiteError by remember { mutableStateOf(false) }
        var privacyError by remember { mutableStateOf(false) }

        fun validate(): Boolean {
            nameError = editName.isBlank()
            authorError = editAuthor.isBlank()
            
            emailError = editEmail.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(editEmail).matches()
            websiteError = editWebsite.isNotBlank() && !Patterns.WEB_URL.matcher(editWebsite).matches()
            privacyError = editPrivacyPolicy.isNotBlank() && !Patterns.WEB_URL.matcher(editPrivacyPolicy).matches()
            
            return !nameError && !authorError && !emailError && !websiteError && !privacyError
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_details)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { 
                            editName = it
                            nameError = false
                        },
                        label = { Text(stringResource(R.string.package_name)) },
                        singleLine = true,
                        isError = nameError,
                        supportingText = if (nameError) { { Text(stringResource(R.string.pkg_name_required_error)) } } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAuthor,
                        onValueChange = { 
                            editAuthor = it
                            authorError = false
                        },
                        label = { Text(stringResource(R.string.author)) },
                        singleLine = true,
                        isError = authorError,
                        supportingText = if (authorError) { { Text(stringResource(R.string.author_required_error)) } } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Advanced Options Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced Options (Optional)", color = MaterialTheme.colorScheme.primary)
                        Icon(
                            if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    AnimatedVisibility(visible = showAdvanced) {
                        Column {
                            OutlinedTextField(
                                value = editEmail,
                                onValueChange = { 
                                    editEmail = it 
                                    emailError = false
                                },
                                label = { Text(stringResource(R.string.email)) },
                                singleLine = true,
                                isError = emailError,
                                supportingText = if (emailError) { { Text("Invalid email format") } } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editWebsite,
                                onValueChange = { 
                                    editWebsite = it 
                                    websiteError = false
                                },
                                label = { Text(stringResource(R.string.website)) },
                                singleLine = true,
                                isError = websiteError,
                                supportingText = if (websiteError) { { Text(stringResource(R.string.invalid_url_error)) } } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editPrivacyPolicy,
                                onValueChange = { 
                                    editPrivacyPolicy = it 
                                    privacyError = false
                                },
                                label = { Text(stringResource(R.string.privacy_policy_website)) },
                                singleLine = true,
                                isError = privacyError,
                                supportingText = if (privacyError) { { Text(stringResource(R.string.invalid_url_error)) } } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editLicense,
                                onValueChange = { editLicense = it },
                                label = { Text(stringResource(R.string.license_website)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    if (validate()) {
                                        viewModel.updatePackageDetails(
                                            name = editName,
                                            author = editAuthor,
                                            email = editEmail,
                                            website = editWebsite,
                                            privacyPolicy = editPrivacyPolicy,
                                            license = editLicense
                                        )
                                        showEditDialog = false
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (validate()) {
                        viewModel.updatePackageDetails(
                            name = editName,
                            author = editAuthor,
                            email = editEmail,
                            website = editWebsite,
                            privacyPolicy = editPrivacyPolicy,
                            license = editLicense
                        )
                        showEditDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text(stringResource(R.string.permission_required_title)) },
            text = { Text(stringResource(R.string.permission_required_rationale)) },
            confirmButton = {
                Button(onClick = {
                    showPermissionRationaleDialog = false
                    permissionState.launchPermissionRequest()
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { Text(stringResource(R.string.permission_required_title)) },
            text = { Text(stringResource(R.string.permission_permanently_denied_message)) },
            confirmButton = {
                Button(onClick = {
                    showPermissionSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsDialog = false }) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }

    if (showDeleteStickerDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteStickerDialog = null },
            title = { Text(stringResource(R.string.delete_sticker_title)) },
            text = { Text(stringResource(R.string.delete_sticker_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteStickerDialog?.let { viewModel.deleteSticker(it.id) }
                        showDeleteStickerDialog = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStickerDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    if (showDeleteSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectionDialog = false },
            title = { Text(stringResource(R.string.delete_selection_title, selectedStickers.size)) },
            text = { Text(stringResource(R.string.delete_selection_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStickers(selectedStickers.toList())
                        selectedStickers = emptySet()
                        showDeleteSelectionDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPreviewDialog != null) {
        val stickerToPreview = showPreviewDialog!!
        AlertDialog(
            onDismissRequest = { showPreviewDialog = null },
            title = { Text(stringResource(R.string.sticker_preview)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(context.filesDir, stickerToPreview.imageFile))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    
                    if (stickerToPreview.emojis.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant, // Changed to surfaceVariant as safer option
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stickerToPreview.emojis.joinToString("  "), // Added spacing
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val title = if (isSelectionMode) {
                stringResource(R.string.selected_count, selectedStickers.size)
            } else if (stickerPackageState is UiState.Success) {
                (stickerPackageState as UiState.Success).data.stickerPackage.name
            } else {
                stringResource(R.string.loading)
            }
            
            // Smoothly animate background color change
            val topBarColor by animateColorAsState(
                targetValue = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                label = "TopBarColor"
            )

            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                     if (isSelectionMode) {
                         IconButton(onClick = { selectedStickers = emptySet() }) {
                             Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_selection))
                         }
                     } else {
                         IconButton(onClick = { navController.popBackStack() }) {
                             Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                         }
                     }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteSelectionDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                        }
                    } else if (stickerPackageState is UiState.Success) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
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
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    if (stickerPackageState is UiState.Success) {
                        val isPackFull = (stickerPackageState as UiState.Success).data.stickers.size >= 30
                        
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.sendStickerPack() },
                            containerColor = WhatsAppGreen,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            expanded = true,
                            icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }, 
                            text = { Text(stringResource(R.string.add_to_whatsapp), fontWeight = FontWeight.Bold) }
                        )
                        
                        if (!isPackFull) {
                            FloatingActionButton(
                                onClick = {
                                    if (isAnimated) {
                                        Toast.makeText(context, context.getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
                                        return@FloatingActionButton
                                    }
                                    if (permissionState.status.isGranted) {
                                        val mimeType = if (isAnimated) "video/*" else "image/*"
                                        imagePickerLauncher.launch(mimeType)
                                    } else if (permissionState.status.shouldShowRationale) {
                                        showPermissionRationaleDialog = true
                                    } else {
                                        permissionState.launchPermissionRequest()
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_sticker))
                            }
                        }
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
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfied, // Using a standard icon
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(80.dp).alpha(0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.pack_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.tap_plus_hint), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                                StickerItemView(
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
                                        } else {
                                            showPreviewDialog = sticker
                                        }
                                    },
                                    onDelete = { showDeleteStickerDialog = sticker }
                                )
                            }
                        }
                    }
                }
                is UiState.Empty -> {
                    Text(stringResource(R.string.pack_not_found), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerItemView(
    sticker: Sticker,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(sticker.imageFile) {
        ImageRequest.Builder(context)
            .data(File(context.filesDir, sticker.imageFile))
            .crossfade(true)
            .build()
    }
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp)) // Ensure ripple respects the shape
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit, // Fix content scale
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp) 
            )

            if (sticker.emojis.isNotEmpty() && !isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sticker.emojis.take(3).joinToString(""),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isSelectionMode) {
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
                            contentDescription = stringResource(R.string.selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun safeEncodeUri(uri: String): String {
    return URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())
}
