@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.AlbumItem
import com.sonora.music.AudioStore
import com.sonora.music.CACHE_ID
import com.sonora.music.DOWNLOADS_ID
import com.sonora.music.LIKED_ID
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track

@Composable
fun LibraryScreen(vm: PlayerViewModel, bottomPad: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openId != null) { openId = null }

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = openId,
            transitionSpec = {
                (fadeIn(tween(250, delayMillis = 60)) +
                    scaleIn(initialScale = 0.94f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))) togetherWith
                    fadeOut(tween(120))
            },
            label = "library"
        ) { id ->
            if (id == null) {
                LibraryHome(vm, bottomPad, onOpen = { openId = it })
            } else {
                PlaylistDetail(vm, id, bottomPad, onBack = { openId = null })
            }
        }
    }
}

@Composable
private fun LibraryHome(vm: PlayerViewModel, bottomPad: androidx.compose.ui.unit.Dp, onOpen: (String) -> Unit) {
    val store = vm.store
    var showNew by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)) {
                Text(
                    tr("Raccolta"),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
                if (store.userName.isNotBlank()) {
                    Text(
                        "Profilo locale di ${store.userName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            PlaylistCard(
                title = "Brani che ti piacciono",
                subtitle = "${store.liked.size} brani",
                cover = null,
                heart = true,
                onClick = { onOpen(LIKED_ID) }
            )
        }

        item {
            val downloadedCount = store.played.count { it.id in AudioStore.downloadedIds }
            PlaylistCard(
                title = "Scaricati",
                subtitle = "$downloadedCount brani · disponibili offline",
                cover = null,
                heart = false,
                icon = Icons.Rounded.Download,
                onClick = { onOpen(DOWNLOADS_ID) }
            )
        }

        item {
            val cachedCount = store.played.count { it.id in AudioStore.cachedIds }
            PlaylistCard(
                title = "In cache",
                subtitle = "$cachedCount brani · salvati automaticamente",
                cover = null,
                heart = false,
                icon = Icons.Rounded.Storage,
                onClick = { onOpen(CACHE_ID) }
            )
        }

        item {
            FilledTonalButton(onClick = { showNew = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Nuova playlist"))
            }
        }

        // Le playlist si vedono come gli album: copertina quadrata e solo il nome.
        items(store.playlists.chunked(2), key = { row -> row.first().id }) { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { p ->
                    AlbumCard(
                        AlbumItem(p.name, "", p.tracks.firstOrNull()?.thumb, p.id),
                        Modifier.weight(1f)
                    ) { onOpen(p.id) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (showNew) {
        NewPlaylistDialog(
            onDismiss = { showNew = false },
            onCreate = { name ->
                store.createPlaylist(name)
                showNew = false
            }
        )
    }
}

@Composable
private fun PlaylistCard(
    title: String,
    subtitle: String,
    cover: String?,
    heart: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else if (heart) {
                Box(
                    Modifier
                        .size(60.dp)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                Artwork(cover, Modifier.size(60.dp), RoundedCornerShape(18.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetail(vm: PlayerViewModel, id: String, bottomPad: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
    val store = vm.store
    val isLikedList = id == LIKED_ID
    val isCache = id == CACHE_ID
    val isDownloads = id == DOWNLOADS_ID
    val isSpecial = isLikedList || isCache || isDownloads
    val playlist = store.playlists.firstOrNull { it.id == id }

    if (!isSpecial && playlist == null) {
        // La playlist è stata eliminata.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val title = when {
        isLikedList -> "Brani che ti piacciono"
        isCache -> "In cache"
        isDownloads -> "Scaricati"
        else -> playlist?.name.orEmpty()
    }
    val tracks: List<Track> = when {
        isLikedList -> store.liked.toList()
        isCache -> store.played.filter { it.id in AudioStore.cachedIds }
        isDownloads -> store.played.filter { it.id in AudioStore.downloadedIds }
        else -> playlist?.tracks.orEmpty()
    }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Indietro"))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!isSpecial) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = tr("Elimina playlist"))
                }
            }
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = { vm.play(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Riproduci"))
            }
        }

        if (tracks.isEmpty()) {
            Message(
                when {
                    isLikedList -> "Tocca il cuore su un brano per salvarlo qui"
                    isCache -> "I brani che ascolti vengono salvati qui in automatico"
                    isDownloads -> "Tieni premuto su un brano e scegli Scarica"
                    else -> "Questa playlist è vuota"
                }
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        selected = vm.nowPlaying?.id == track.id,
                        liked = store.isLiked(track.id),
                        onClick = { vm.play(tracks, index) },
                        onLike = { store.toggleLike(track) },
                        onMore = { vm.menuTrack = track },
                        modifier = Modifier.animateItem(),
                        trailing = when {
                            isCache -> {
                                {
                                    IconButton(onClick = { vm.download(track) }) {
                                        Icon(Icons.Rounded.Download, contentDescription = tr("Scarica"))
                                    }
                                }
                            }

                            isDownloads -> {
                                {
                                    IconButton(onClick = { vm.removeOffline(track.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = tr("Rimuovi dai download"))
                                    }
                                }
                            }

                            !isLikedList -> {
                                {
                                    IconButton(onClick = { store.removeFromPlaylist(id, track.id) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = tr("Rimuovi dalla playlist"))
                                    }
                                }
                            }

                            else -> null
                        }
                    )
                }
            }
        }
    }

    addTrack?.let { AddToPlaylistDialog(store, it) { addTrack = null } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Eliminare la playlist?")) },
            text = { Text("«$title» verrà rimossa da questo telefono.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deletePlaylist(id)
                }) { Text(tr("Elimina")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Annulla")) } }
        )
    }
}
