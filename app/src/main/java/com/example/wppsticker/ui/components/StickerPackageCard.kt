package com.example.wppsticker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wppsticker.R
import com.example.wppsticker.data.local.Sticker
import java.io.File

@Composable
fun StickerPackageCard(
    name: String,
    author: String,
    isAnimated: Boolean,
    stickers: List<Sticker>,
    onClick: () -> Unit,
    onSend: (() -> Unit)? = null, // Optional actions
    onDelete: (() -> Unit)? = null,
    isFull: Boolean = false,
    isSelected: Boolean = false
) {
    val context = LocalContext.current
    val stickersCount = stickers.size

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isFull, onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFull) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(borderWidth, borderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // --- Header Row ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Info (Name & Author)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name, 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            color = if (isFull) Color.Gray else MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Text(
                                stringResource(R.string.stickers_by_author, stickersCount, author), 
                                style = MaterialTheme.typography.bodySmall, 
                                color = Color.Gray
                            )
                            if (isAnimated) { 
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(stringResource(R.string.animated_badge), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }

                    // Actions (Only show if callbacks provided)
                    if (onSend != null) {
                        IconButton(onClick = onSend) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, 
                                contentDescription = stringResource(R.string.send_to_whatsapp_desc),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = stringResource(R.string.delete_pack_desc),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    if (isFull) {
                         Text(stringResource(R.string.package_full_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Preview Grid ---
                if (stickers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val maxPreview = 6
                        val stickersToShow = stickers.take(maxPreview)
                        val remainingCount = stickers.size - maxPreview

                        stickersToShow.forEachIndexed { index, sticker ->
                            val isLastInPreview = index == stickersToShow.lastIndex
                            val hasMore = remainingCount > 0

                            if (isLastInPreview && hasMore) {
                                // Last item with overlay
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(context.filesDir, sticker.imageFile))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(R.string.more_stickers_desc),
                                        modifier = Modifier.fillMaxSize(),
                                        colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.6f), blendMode = BlendMode.SrcOver)
                                    )
                                    Text(
                                        text = stringResource(R.string.remaining_stickers_count, remainingCount + 1),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // Regular sticker image
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(context.filesDir, sticker.imageFile))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.empty_pack), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        
        if (isSelected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
