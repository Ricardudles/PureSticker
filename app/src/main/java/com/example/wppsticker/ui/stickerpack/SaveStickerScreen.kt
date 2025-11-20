package com.example.wppsticker.ui.stickerpack

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.nav.Screen
import com.example.wppsticker.ui.util.LoadingDialog
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveStickerScreen(
    navController: NavController,
    viewModel: SaveStickerViewModel = hiltViewModel()
) {
    val packages by viewModel.stickerPackages.collectAsState()
    val saveFinished by viewModel.saveFinished.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    
    // Derive isBusy from loadingMessage since we removed isBusy from ViewModel
    val isBusy = loadingMessage != null
    
    val context = LocalContext.current

    var selectedPackage by remember { mutableStateOf<StickerPackage?>(null) }
    
    // Changed: Manage emojis as a list
    var selectedEmojis by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    var showNewPackageDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.events.collectLatest { event ->
            when(event) {
                is SaveStickerEvent.ShowDuplicateDialog -> {
                    showDuplicateDialog = event.onConfirm
                }
                is SaveStickerEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(saveFinished) {
        if (saveFinished) {
            navController.popBackStack(Screen.Home.name, false)
        }
    }

    // --- Dialogs ---
    if (loadingMessage != null) {
        LoadingDialog(message = loadingMessage!!)
    }

    if (showDuplicateDialog != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = null },
            title = { Text("Duplicate Sticker") },
            text = { Text("This image already exists in this package. Do you want to add it anyway?") },
            confirmButton = {
                Button(onClick = {
                    showDuplicateDialog?.invoke()
                    showDuplicateDialog = null
                }) { Text("Add Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showNewPackageDialog) {
        var newPackageName by remember { mutableStateOf("") }
        var newPackageAuthor by remember { mutableStateOf("") }
        var newPackageEmail by remember { mutableStateOf("") }
        var newPackageWebsite by remember { mutableStateOf("") }
        var newPackagePrivacyPolicy by remember { mutableStateOf("") }
        var newPackageLicense by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewPackageDialog = false },
            title = { Text("Create New Package") },
            text = { 
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = newPackageName, 
                        onValueChange = { newPackageName = it }, 
                        label = { Text("Package Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newPackageAuthor, 
                        onValueChange = { newPackageAuthor = it }, 
                        label = { Text("Author *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newPackageEmail, 
                        onValueChange = { newPackageEmail = it }, 
                        label = { Text("Email (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newPackageWebsite, 
                        onValueChange = { newPackageWebsite = it }, 
                        label = { Text("Website (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        placeholder = { Text("https://...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newPackagePrivacyPolicy, 
                        onValueChange = { newPackagePrivacyPolicy = it }, 
                        label = { Text("Privacy Policy URL (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newPackageLicense, 
                        onValueChange = { newPackageLicense = it }, 
                        label = { Text("License URL (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.createNewPackage(
                        name = newPackageName,
                        author = newPackageAuthor,
                        email = newPackageEmail,
                        website = newPackageWebsite,
                        privacyPolicy = newPackagePrivacyPolicy,
                        licenseAgreement = newPackageLicense
                    ) { createdPackage ->
                        selectedPackage = createdPackage
                        showNewPackageDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPackageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Main UI ---
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Save Sticker") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            ) 
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painter = rememberAsyncImagePainter(viewModel.stickerUri), contentDescription = "Final Sticker", modifier = Modifier.size(150.dp))
                Spacer(modifier = Modifier.height(16.dp))
                
                // --- Emoji Selector UI ---
                Text("Select Emojis (Max 3)", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = selectedEmojis.joinToString(" "),
                    onValueChange = { }, // Read-only
                    readOnly = true,
                    label = { Text("Emojis") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEmojiPicker = true }, // Open picker on click
                    trailingIcon = {
                        IconButton(onClick = { showEmojiPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Select Emojis")
                        }
                    },
                    enabled = false, // Disable typing, but click works on Box above or Icon
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = selectedPackage?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select a Package") },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        packages.forEach { pack ->
                            DropdownMenuItem(
                                text = { Text(pack.name) }, 
                                onClick = { 
                                    selectedPackage = pack
                                    expanded = false 
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("+ Create New Package") }, 
                            onClick = { 
                                showNewPackageDialog = true 
                                expanded = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { 
                        selectedPackage?.let { 
                            viewModel.saveSticker(it, selectedEmojis.joinToString(",")) 
                        } 
                    },
                    enabled = selectedPackage != null && selectedEmojis.isNotEmpty() && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Sticker to Package")
                }
            }

            if (showEmojiPicker) {
                EmojiPickerSheet(
                    selectedEmojis = selectedEmojis,
                    onEmojiToggle = { emoji ->
                        if (selectedEmojis.contains(emoji)) {
                            selectedEmojis = selectedEmojis - emoji
                        } else if (selectedEmojis.size < 3) {
                            selectedEmojis = selectedEmojis + emoji
                        } else {
                            // Already 3 selected, maybe show toast
                            Toast.makeText(context, "Max 3 emojis allowed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismissRequest = { showEmojiPicker = false }
                )
            }
        }
    }
}
