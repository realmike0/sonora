@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel

/** Coda di riproduzione: tocca per saltare a un brano, sposta in su o rimuovi. */
@Composable
fun QueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(
                "Coda (${vm.queue.size})",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            if (vm.queue.isEmpty()) {
                Text("Niente in coda", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            LazyColumn {
                itemsIndexed(vm.queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                    val current = i == vm.queueIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { vm.jumpTo(i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(t.thumb, Modifier.size(48.dp), RoundedCornerShape(12.dp), px = 128)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                t.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (i > 0) IconButton(onClick = { vm.moveInQueue(i, i - 1) }) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Sposta su")
                        }
                        if (!current) IconButton(onClick = { vm.removeFromQueue(i) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Rimuovi")
                        }
                    }
                }
            }
        }
    }
}
