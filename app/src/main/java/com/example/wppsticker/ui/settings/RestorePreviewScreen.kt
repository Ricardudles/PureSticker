package com.example.wppsticker.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wppsticker.data.backup.BackupPackageDto

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
                Toast.makeText(context, "Restore successful!", Toast.LENGTH_LONG).show()
                navController.popBackStack()
            }
            is RestoreUiState.Error -> {
                val message = (restoreState as RestoreUiState.Error).message
                Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
            }
            else -> { /* Idle or Loading */ }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Restore from Backup") }) },
        bottomBar = {
            Button(
                onClick = { viewModel.restoreSelected() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = selectedPackages.isNotEmpty() && restoreState !is RestoreUiState.Loading
            ) {
                Text("Restore Selected (${selectedPackages.size})")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (restoreState is RestoreUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(backupInfo.backupPackage.name, fontWeight = FontWeight.Bold)
            Text("Status: ${backupInfo.status.name}", color = if (backupInfo.status == RestoreStatus.NEW) Color.Green else Color.Gray)
        }
        if (backupInfo.status == RestoreStatus.NEW) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        } else {
             Checkbox(checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}
