package com.example.wppsticker.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestorePreviewScreen(
    navController: NavController,
    viewModel: RestoreViewModel = hiltViewModel()
) {
    val backupPackages by viewModel.backupPackages.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(restoreState) {
        when (restoreState) {
            is RestoreUiState.Success -> {
                Toast.makeText(context, context.getString(R.string.restore_success_toast), Toast.LENGTH_LONG).show()
                navController.popBackStack()
            }
            is RestoreUiState.Error -> {
                val message = (restoreState as RestoreUiState.Error).message
                Toast.makeText(context, context.getString(R.string.restore_error_toast, message), Toast.LENGTH_LONG).show()
            }
            else -> { /* Idle or Loading */ }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.restore_preview_title), fontWeight = FontWeight.Bold) },
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
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
                    .navigationBarsPadding() // <- FIX: Added safe area padding
            ) {
                Button(
                    onClick = { viewModel.restoreSelected() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedPackages.isNotEmpty() && restoreState !is RestoreUiState.Loading
                ) {
                    if (restoreState is RestoreUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), 
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.restore_selected_button, selectedPackages.size))
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (restoreState is RestoreUiState.Loading && backupPackages.isEmpty()) { // Initial Loading
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(backupPackages) { backupInfo ->
                        RestoreItem(
                            backupInfo = backupInfo,
                            isSelected = selectedPackages.contains(backupInfo.backupPackage.identifier),
                            onToggle = { viewModel.toggleSelection(backupInfo.backupPackage.identifier) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreItem(
    backupInfo: BackupPackageInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val isEnabled = backupInfo.status == RestoreStatus.NEW
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected, 
                onCheckedChange = { onToggle() },
                enabled = isEnabled,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backupInfo.backupPackage.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.stickers_by_author, backupInfo.backupPackage.stickers.size, backupInfo.backupPackage.author), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )
                
                if (!isEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.package_exists_label), 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color.Yellow
                        )
                    }
                }
            }
        }
    }
}
