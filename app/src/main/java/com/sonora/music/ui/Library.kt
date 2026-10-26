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
import androidx.compose.material.icons.rounded.Favorite
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
import com.sonora.music.LIKED_ID
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track

@Composable
fun LibraryScreen(vm: PlayerViewModel, modifier: Modifier = Modifier) {
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
                LibraryHome(vm, onOpen = { openId = it })
            } else {
                PlaylistDetail(vm, id, onBack = { openId = null })
            }
        }
    }
}

@Composable
private fun LibraryHome(vm: PlayerViewModel, onOpen: (String) -> Unit) {
    val store = vm.store
    var showNew by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)) {
                Text(
                    "Libreria",
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
            FilledTonalButton(onClick = { showNew = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nuova playlist")
            }
        }

        items(store.playlists, key = { it.id }) { p ->
            PlaylistCard(
                title = p.name,
                subtitle = "${p.tracks.size} brani" + if (p.fromGoogle) " · da YouTube Music" else "",
                cover = p.tracks.firstOrNull()?.thumb,
                heart = false,
                onClick = { onOpen(p.id) },
                modifier = Modifier.animateItem()
            )
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
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (heart) {
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
private fun PlaylistDetail(vm: PlayerViewModel, id: String, onBack: () -> Unit) {
    val store = vm.store
    val isLikedList = id == LIKED_ID
    val playlist = store.playlists.firstOrNull { it.id == id }

    if (!isLikedList && playlist == null) {
        // La playlist è stata eliminata.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val title = if (isLikedList) "Brani che ti piacciono" else playlist?.name.orEmpty()
    val tracks: List<Track> = if (isLikedList) store.liked.toList() else playlist?.tracks.orEmpty()
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!isLikedList) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Elimina playlist")
                }
            }
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = { vm.play(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Riproduci")
            }
        }

        if (tracks.isEmpty()) {
            Message(if (isLikedList) "Tocca il cuore su un brano per salvarlo qui" else "Questa playlist è vuota")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        selected = vm.nowPlaying?.id == track.id,
                        liked = store.isLiked(track.id),
                        onClick = { vm.play(tracks, index) },
                        onLike = { store.toggleLike(track) },
                        onMore = { addTrack = track },
                        modifier = Modifier.animateItem(),
                        trailing = if (!isLikedList) {
                            {
                                IconButton(onClick = { store.removeFromPlaylist(id, track.id) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Rimuovi dalla playlist")
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    addTrack?.let { AddToPlaylistDialog(store, it) { addTrack = null } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la playlist?") },
            text = { Text("«$title» verrà rimossa da questo telefono.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deletePlaylist(id)
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
}
