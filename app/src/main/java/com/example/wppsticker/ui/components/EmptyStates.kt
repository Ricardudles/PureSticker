package com.example.wppsticker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wppsticker.R

@Composable
fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Collections,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(100.dp).alpha(0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.no_packs_found), color = Color.Gray, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tap_plus_create), color = Color.DarkGray)
        }
    }
}

@Composable
fun EmptyStateFiltered(isAnimated: Boolean, onCreateClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Image, 
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(100.dp).alpha(0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isAnimated) stringResource(R.string.no_anim_packs_found) else stringResource(R.string.no_static_packs_found),
                color = Color.Gray, 
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onCreateClick) {
                Text(stringResource(R.string.create_new_pack))
            }
        }
    }
}
