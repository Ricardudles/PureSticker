package com.example.wppsticker.ui.stickerpack

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.nav.Screen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveStickerScreen(
    navController: NavController,
    viewModel: SaveStickerViewModel = hiltViewModel()
) {
    val packages by viewModel.stickerPackages.collectAsState()
    val saveFinished by viewModel.saveFinished.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val context = LocalContext.current

    var selectedPackage by remember { mutableStateOf<StickerPackage?>(null) }
    var emojis by remember { mutableStateOf("") }
    var showNewPackageDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(saveFinished) {
        if (saveFinished) {
            navController.popBackStack(Screen.Home.name, false)
        }
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Save Sticker") }) }
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
                
                TextField(
                    value = emojis, 
                    onValueChange = { emojis = it }, 
                    label = { Text("Enter Emojis (max 3, comma separated)") },
                    // Configure keyboard to show emojis if possible (depends on keyboard app)
                    // but mostly sets action to Done
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("⚠️ Do not enter text, only emojis! (e.g. 😂, 🔥)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                    onClick = { selectedPackage?.let { viewModel.saveSticker(it, emojis) } },
                    enabled = selectedPackage != null && emojis.isNotBlank() && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Sticker to Package")
                }
            }

            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
