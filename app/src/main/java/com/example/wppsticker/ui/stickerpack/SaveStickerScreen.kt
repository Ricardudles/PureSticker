package com.example.wppsticker.ui.stickerpack

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wppsticker.R
import com.example.wppsticker.data.local.StickerPackage
import com.example.wppsticker.nav.Screen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveStickerScreen(
    navController: NavController,
    viewModel: SaveStickerViewModel = hiltViewModel()
) {
    val preSelectedPackage by viewModel.preSelectedPackage.collectAsState()
    val context = LocalContext.current

    // Get Arguments
    val isAnimatedArg = remember { 
        navController.currentBackStackEntry?.arguments?.getBoolean("isAnimated") ?: false 
    }

    var selectedPackage by remember { mutableStateOf<StickerPackage?>(null) }
    
    // Pre-selection logic with Compatibility Check
    LaunchedEffect(preSelectedPackage) {
        if (preSelectedPackage != null && selectedPackage == null) {
            // Only pre-select if compatible
            if (preSelectedPackage!!.animated == isAnimatedArg) { 
                selectedPackage = preSelectedPackage
            }
        }
    }
    
    // Using rememberSaveable to keep emojis when navigating back
    var selectedEmojis by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    // --- Helper for Navigation ---
    val navigateToSelection = {
        if (selectedEmojis.isNotEmpty()) {
            val encodedUri = URLEncoder.encode(viewModel.stickerUri.toString(), StandardCharsets.UTF_8.toString())
            val encodedEmojis = URLEncoder.encode(selectedEmojis.joinToString(","), StandardCharsets.UTF_8.toString())
            val preSelectedId = selectedPackage?.id ?: -1
            navController.navigate(
                "${Screen.PackageSelection.name}?isAnimated=$isAnimatedArg&stickerUri=$encodedUri&emojis=$encodedEmojis&preSelectedPackageId=$preSelectedId"
            )
        } else {
            Toast.makeText(context, context.getString(R.string.emoji_required_error), Toast.LENGTH_SHORT).show()
        }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    shape = RoundedCornerShape(12.dp),
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
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { navigateToSelection() },
                    enabled = selectedEmojis.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        stringResource(R.string.select_package_placeholder), 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold
                    )
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
