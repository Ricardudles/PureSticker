package com.example.wppsticker.ui.stickerpack

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.nav.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveStickerScreen(
    navController: NavController,
    viewModel: SaveStickerViewModel = hiltViewModel()
) {
    val packages by viewModel.stickerPackages.collectAsState()
    val saveFinished by viewModel.saveFinished.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()

    var selectedPackage by remember { mutableStateOf<StickerPackage?>(null) }
    var emojis by remember { mutableStateOf("") }
    var showNewPackageDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(saveFinished) {
        if (saveFinished) {
            navController.popBackStack(Screen.Home.name, false)
        }
    }

    if (showNewPackageDialog) {
        var newPackageName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPackageDialog = false },
            title = { Text("Create New Package") },
            text = { TextField(value = newPackageName, onValueChange = { newPackageName = it }) },
            confirmButton = {
                Button(onClick = { 
                    viewModel.createNewPackage(newPackageName) { createdPackage ->
                        selectedPackage = createdPackage
                        showNewPackageDialog = false
                    }
                }) { Text("Create") }
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painter = rememberAsyncImagePainter(viewModel.stickerUri), contentDescription = "Final Sticker", modifier = Modifier.size(150.dp))
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(value = emojis, onValueChange = { emojis = it }, label = { Text("Enter Emojis (comma separated)") })
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = selectedPackage?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select a Package") },
                        modifier = Modifier.menuAnchor(),
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
                            text = { Text("+ New Package") }, 
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
                    enabled = selectedPackage != null && emojis.isNotBlank() && !isBusy
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
