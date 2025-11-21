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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wppsticker.R
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
    val preSelectedPackage by viewModel.preSelectedPackage.collectAsState()
    val saveFinished by viewModel.saveFinished.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    
    val isBusy = loadingMessage != null
    val context = LocalContext.current

    var selectedPackage by remember { mutableStateOf<StickerPackage?>(null) }
    
    LaunchedEffect(preSelectedPackage) {
        if (preSelectedPackage != null && selectedPackage == null) {
            selectedPackage = preSelectedPackage
        }
    }
    
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

    if (showNewPackageDialog) {
        var newPackageName by remember { mutableStateOf("") }
        var newPackageAuthor by remember { mutableStateOf("") }
        var newPackageEmail by remember { mutableStateOf("") }
        var newPackageWebsite by remember { mutableStateOf("") }
        var newPackagePrivacyPolicy by remember { mutableStateOf("") }
        var newPackageLicense by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewPackageDialog = false },
            title = { Text(stringResource(R.string.create_package_title)) },
            text = { 
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = newPackageName, onValueChange = { newPackageName = it }, label = { Text(stringResource(R.string.package_name_required)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageAuthor, onValueChange = { newPackageAuthor = it }, label = { Text(stringResource(R.string.author_required)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageEmail, onValueChange = { newPackageEmail = it }, label = { Text(stringResource(R.string.email_optional)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageWebsite, onValueChange = { newPackageWebsite = it }, label = { Text(stringResource(R.string.website_optional)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackagePrivacyPolicy, onValueChange = { newPackagePrivacyPolicy = it }, label = { Text(stringResource(R.string.privacy_policy_optional)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPackageLicense, onValueChange = { newPackageLicense = it }, label = { Text(stringResource(R.string.license_optional)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.createNewPackage(newPackageName, newPackageAuthor, newPackageEmail, newPackageWebsite, newPackagePrivacyPolicy, newPackageLicense) { createdPackage ->
                        selectedPackage = createdPackage
                        showNewPackageDialog = false
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showNewPackageDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // --- Main UI ---
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.save_sticker_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                        Text(stringResource(R.string.select_emojis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = selectedEmojis.joinToString(" "),
                            onValueChange = { }, 
                            readOnly = true,
                            placeholder = { Text(stringResource(R.string.tap_to_select)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEmojiPicker = true }, 
                            trailingIcon = {
                                IconButton(onClick = { showEmojiPicker = true }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.select))
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

                        Text(stringResource(R.string.add_to_package), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedPackage?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text(stringResource(R.string.select_package_placeholder)) },
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
                                    text = { Text(stringResource(R.string.create_new_package_option), color = MaterialTheme.colorScheme.primary) }, 
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
                    Text(stringResource(R.string.save_sticker_btn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (showEmojiPicker) {
                EmojiPickerSheet(
                    selectedEmojis = selectedEmojis,
                    onEmojiToggle = { emoji ->
                        if (selectedEmojis.contains(emoji)) selectedEmojis = selectedEmojis - emoji
                        else if (selectedEmojis.size < 3) selectedEmojis = selectedEmojis + emoji
                        else Toast.makeText(context, context.getString(R.string.max_emojis_error), Toast.LENGTH_SHORT).show()
                    },
                    onDismissRequest = { showEmojiPicker = false }
                )
            }
        }
    }
}
