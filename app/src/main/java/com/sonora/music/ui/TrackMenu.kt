@file:OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.AudioStore
import com.sonora.music.MusicRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track
import com.sonora.music.TrackDetails
import java.text.NumberFormat
import java.util.Locale

/** Menu a tendina aperto con la pressione prolungata su un brano. */
@Composable
fun TrackMenuSheet(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val store = vm.store
    var addingToPlaylist by remember { mutableStateOf(false) }

    val details by produceState<TrackDetails?>(null, track.id) {
        value = MusicRepository.details(track.id)
    }

    val liked = store.isLiked(track.id)
    val downloaded = track.id in AudioStore.downloadedIds
    val cached = track.id in AudioStore.cachedIds
    val progress = AudioStore.downloading[track.id]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Intestazione con copertina e titolo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.thumb, Modifier.size(72.dp), RoundedCornerShape(20.dp), px = 256)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Info del brano
            Column(
                Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (track.durationSec > 0) InfoLine("Durata", formatTime(track.durationSec * 1000))
                InfoLine(
                    "Pubblicazione",
                    details?.published ?: if (details == null) "Caricamento…" else "Non disponibile"
                )
                details?.views?.let {
                    InfoLine("Visualizzazioni", NumberFormat.getInstance(Locale.ITALY).format(it))
                }
                InfoLine(
                    "Offline",
                    when {
                        downloaded -> "Scaricato"
                        progress != null -> "Download in corso…"
                        cached -> "In cache"
                        else -> "No"
                    }
                )
            }

            MenuAction(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (liked) "Rimuovi dai preferiti" else "Aggiungi ai preferiti"
            ) {
                store.toggleLike(track)
            }

            MenuAction(Icons.Rounded.PlaylistAdd, "Aggiungi alla playlist") { addingToPlaylist = true }

            when {
                downloaded -> MenuAction(Icons.Rounded.Delete, "Rimuovi dai download") {
                    vm.removeOffline(track.id)
                    onDismiss()
                }

                progress != null -> MenuAction(
                    icon = Icons.Rounded.Download,
                    label = "Download in corso… ${(progress * 100).toInt()}%",
                    busy = true
                ) {
                    AudioStore.cancelDownload(track.id)
                }

                else -> MenuAction(
                    icon = Icons.Rounded.Download,
                    label = if (cached) "Scarica (già in cache, passa ai download)" else "Scarica"
                ) {
                    vm.download(track)
                }
            }

            if (cached && !downloaded) {
                MenuAction(Icons.Rounded.Storage, "Rimuovi dalla cache") {
                    vm.removeOffline(track.id)
                    onDismiss()
                }
            }
        }
    }

    if (addingToPlaylist) {
        AddToPlaylistDialog(store, track) {
            addingToPlaylist = false
            onDismiss()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        } else {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
