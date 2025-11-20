package com.example.wppsticker.ui.stickerpack

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    
    val isBusy = loadingMessage != null
    val context = LocalContext.current

    var selectedPackage by remember { mutableStateOf<StickerPackage?>(null) }
    var selectedEmojis by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showNewPackageDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.events.collectLatest { event ->
            when(event) {
                is SaveStickerEvent.ShowDuplicateDialog -> showDuplicateDialog = event.onConfirm
                is SaveStickerEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(saveFinished) {
        if (saveFinished) {
            navController.popBackStack(Screen.Home.name, false)
        }
    }

    // --- Dialogs ---
    if (loadingMessage != null) LoadingDialog(message = loadingMessage!!)

    if (showDuplicateDialog != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = null },
            title = { Text("Duplicate Sticker") },
            text = { Text("This image already exists in this package. Do you want to add it anyway?") },
            confirmButton = {
                Button(onClick = { showDuplicateDialog?.invoke(); showDuplicateDialog = null }) { Text("Add Anyway") }
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
                    OutlinedTextField(value = newPackageName, onValueChange = { newPackageName = it }, label = { Text("Package Name *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageAuthor, onValueChange = { newPackageAuthor = it }, label = { Text("Author *") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageEmail, onValueChange = { newPackageEmail = it }, label = { Text("Email (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageWebsite, onValueChange = { newPackageWebsite = it }, label = { Text("Website (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackagePrivacyPolicy, onValueChange = { newPackagePrivacyPolicy = it }, label = { Text("Privacy Policy (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageLicense, onValueChange = { newPackageLicense = it }, label = { Text("License (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.createNewPackage(newPackageName, newPackageAuthor, newPackageEmail, newPackageWebsite, newPackagePrivacyPolicy, newPackageLicense) { createdPackage ->
                        selectedPackage = createdPackage
                        showNewPackageDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewPackageDialog = false }) { Text("Cancel") } }
        )
    }

    // --- Main UI ---
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { 
            TopAppBar(
                title = { Text("Save Sticker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
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
                // Preview Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(viewModel.stickerUri).crossfade(true).build(),
                        contentDescription = "Final Sticker",
                        modifier = Modifier.size(200.dp).padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // --- Form Area ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Emojis (Max 3)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = selectedEmojis.joinToString(" "),
                            onValueChange = { }, 
                            readOnly = true,
                            placeholder = { Text("Tap to select...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEmojiPicker = true }, 
                            trailingIcon = {
                                IconButton(onClick = { showEmojiPicker = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Select")
                                }
                            },
                            enabled = false, 
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledPlaceholderColor = Color.Gray,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Add to Package", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedPackage?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select a package...") },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                packages.forEach { pack ->
                                    DropdownMenuItem(
                                        text = { Text(pack.name) }, 
                                        onClick = { selectedPackage = pack; expanded = false }
                                    )
                                }
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("+ Create New Package", color = MaterialTheme.colorScheme.primary) }, 
                                    onClick = { showNewPackageDialog = true; expanded = false }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { selectedPackage?.let { viewModel.saveSticker(it, selectedEmojis.joinToString(",")) } },
                    enabled = selectedPackage != null && selectedEmojis.isNotEmpty() && !isBusy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Sticker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (showEmojiPicker) {
                EmojiPickerSheet(
                    selectedEmojis = selectedEmojis,
                    onEmojiToggle = { emoji ->
                        if (selectedEmojis.contains(emoji)) selectedEmojis = selectedEmojis - emoji
                        else if (selectedEmojis.size < 3) selectedEmojis = selectedEmojis + emoji
                        else Toast.makeText(context, "Max 3 emojis allowed", Toast.LENGTH_SHORT).show()
                    },
                    onDismissRequest = { showEmojiPicker = false }
                )
            }
        }
    }
}
