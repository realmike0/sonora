package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonora.music.AlbumItem
import com.sonora.music.ArtistRepository
import com.sonora.music.GenreRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track

/** Schermata iniziale della ricerca: generi (e, sotto, i brani riprodotti dopo le ricerche). */
@Composable
fun SearchHome(vm: PlayerViewModel, bottomPad: Dp, onGenre: (String) -> Unit) {
    val store = vm.store
    val searchPlays = store.searchPlays.toList()
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        item {
            Text(
                tr("Generi"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)
            )
        }
        items(GenreRepository.genres.chunked(2), key = { it.first().first }) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (name, color) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(color))
                            .clickable { onGenre(name) }
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (searchPlays.isNotEmpty()) {
            item {
                Text(
                    tr("Riprodotti dopo le tue ricerche"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp)
                )
            }
            itemsIndexed(searchPlays, key = { _, t -> "sp-" + t.id }) { index, track ->
                TrackRow(
                    track = track,
                    selected = vm.nowPlaying?.id == track.id,
                    liked = store.isLiked(track.id),
                    onClick = { vm.play(searchPlays, index, false) },
                    onLike = { store.toggleLike(track) },
                    onMore = { vm.menuTrack = track }
                )
            }
        }
    }
}

/** Playlist di un genere; toccandone una si vedono i brani. */
@Composable
fun GenreScreen(vm: PlayerViewModel, genre: String, bottomPad: Dp, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<AlbumItem?>(null) }
    BackHandler { if (selected != null) selected = null else onBack() }

    val sel = selected
    if (sel != null) {
        PlaylistTracks(vm, sel, bottomPad, onBack = { selected = null })
        return
    }

    var list by remember(genre) { mutableStateOf<List<AlbumItem>?>(null) }
    LaunchedEffect(genre) { list = runCatching { GenreRepository.playlists(genre) }.getOrDefault(emptyList()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Indietro")) }
            Text(genre, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
        }
        val found = list
        when {
            found == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BouncyLoader() }
            found.isEmpty() -> Message(tr("Nessuna playlist trovata"))
            else -> LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = bottomPad + 16.dp)) {
                items(found.chunked(2), key = { it.first().url }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { a -> AlbumCard(a, Modifier.weight(1f)) { selected = a } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Copertina quadrata con il titolo sotto, come gli album. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AlbumCard(album: AlbumItem, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Artwork(album.thumb, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(20.dp), px = 400)
        Text(
            album.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PlaylistTracks(vm: PlayerViewModel, album: AlbumItem, bottomPad: Dp, onBack: () -> Unit) {
    var tracks by remember(album.url) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(album.url) { tracks = runCatching { ArtistRepository.albumTracks(album) }.getOrDefault(emptyList()) }
    val list = tracks

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        item {
            Column {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Indietro")) }
                }
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(album.thumb, Modifier.size(120.dp), RoundedCornerShape(24.dp), px = 400)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        album.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { list?.let { vm.play(it, 0) } },
                    enabled = !list.isNullOrEmpty(),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Riproduci"))
                }
            }
        }
        when {
            list == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { BouncyLoader() } }
            else -> itemsIndexed(list, key = { i, t -> "pl-$i-${t.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    selected = vm.nowPlaying?.id == track.id,
                    liked = vm.store.isLiked(track.id),
                    onClick = { vm.play(list, index) },
                    onLike = { vm.store.toggleLike(track) },
                    onMore = { vm.menuTrack = track }
                )
            }
        }
    }
}
