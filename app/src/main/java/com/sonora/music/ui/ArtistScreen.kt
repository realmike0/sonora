package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.AlbumItem
import com.sonora.music.ArtistPage
import com.sonora.music.ArtistRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.R
import com.sonora.music.Track

/** Caratteri "da copertina": ogni artista ne riceve uno (sempre lo stesso, scelto dal nome). */
private val ARTIST_FONTS = listOf(
    R.font.art_pacifico, R.font.art_bangers, R.font.art_lobster, R.font.art_righteous,
    R.font.art_abril, R.font.art_marker, R.font.art_anton, R.font.art_rubikmono,
    R.font.art_shrikhand, R.font.art_bowlby, R.font.art_chango, R.font.art_bebas,
    R.font.art_dancing, R.font.art_fredoka
).map { FontFamily(Font(it, FontWeight.Normal)) }

private fun hashOf(name: String): Int = name.trim().lowercase().hashCode() and 0x7fffffff

fun artistFont(name: String): FontFamily = ARTIST_FONTS[hashOf(name) % ARTIST_FONTS.size]

/** Colore vivace e mai scuro: tinta casuale, saturazione media, luminosità alta. */
fun artistColor(name: String): Color {
    val h = hashOf(name)
    val hue = ((h / ARTIST_FONTS.size) % 360).toFloat()
    val sat = 0.45f + ((h / 7) % 30) / 100f
    return Color.hsv(hue, sat, 0.97f)
}

private fun nameSize(name: String): TextUnit = when {
    name.length <= 6 -> 84.sp
    name.length <= 10 -> 68.sp
    name.length <= 16 -> 52.sp
    else -> 40.sp
}

private sealed interface ArtistState {
    object Loading : ArtistState
    data class Error(val msg: String) : ArtistState
    data class Ready(val page: ArtistPage) : ArtistState
}

/** Pagina dell'artista: nome gigante nel suo carattere e colore, brani popolari e album. */
@Composable
fun ArtistScreen(vm: PlayerViewModel, name: String, bottomPad: androidx.compose.ui.unit.Dp, onClose: () -> Unit) {
    var attempt by remember { mutableStateOf(0) }
    var state by remember(name) { mutableStateOf<ArtistState>(ArtistState.Loading) }
    var album by remember(name) { mutableStateOf<AlbumItem?>(null) }

    LaunchedEffect(name, attempt) {
        state = ArtistState.Loading
        state = try {
            ArtistState.Ready(ArtistRepository.load(name))
        } catch (e: Exception) {
            ArtistState.Error(e.message ?: "Impossibile caricare l'artista")
        }
    }

    BackHandler { if (album != null) album = null else onClose() }

    val accent = artistColor(name)
    val bg = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), endY = 1200f))
    ) {
        val current = album
        if (current != null) {
            AlbumView(vm, current, accent, bottomPad, onBack = { album = null })
        } else {
            when (val s = state) {
                ArtistState.Loading -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    BackRow(onClose)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BouncyLoader() }
                }

                is ArtistState.Error -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    BackRow(onClose)
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(s.msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { attempt++ }) { Text("Riprova") }
                    }
                }

                is ArtistState.Ready -> ArtistContent(vm, s.page, accent, bottomPad, onClose) { album = it }
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        Icon(Icons.Rounded.ArrowBack, contentDescription = "Indietro")
    }
}

@Composable
private fun ArtistContent(
    vm: PlayerViewModel,
    page: ArtistPage,
    accent: Color,
    bottomPad: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit,
    onAlbum: (AlbumItem) -> Unit
) {
    val songs = page.songs.take(10)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPad + 16.dp)
    ) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding()) {
                BackRow(onClose)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        page.name,
                        style = TextStyle(
                            fontFamily = artistFont(page.name),
                            fontWeight = FontWeight.Normal,
                            fontSize = nameSize(page.name),
                            lineHeight = nameSize(page.name) * 0.98f,
                            color = accent
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (page.image != null) {
                        Spacer(Modifier.width(12.dp))
                        Artwork(page.image, Modifier.size(72.dp), CircleShape, px = 256)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.play(songs, 0) },
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Riproduci")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (songs.isNotEmpty()) {
            item(key = "songs-title") { SectionTitle("Brani popolari") }
            itemsIndexed(songs, key = { _, t -> "s-" + t.id }) { index, track ->
                TrackRow(
                    track = track,
                    selected = vm.nowPlaying?.id == track.id,
                    liked = vm.store.isLiked(track.id),
                    onClick = { vm.play(songs, index) },
                    onLike = { vm.store.toggleLike(track) },
                    onMore = { vm.menuTrack = track }
                )
            }
        }

        if (page.albums.isNotEmpty()) {
            item(key = "albums-title") { SectionTitle("Album e singoli") }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(page.albums, key = { it.url }) { a ->
                        Column(
                            Modifier
                                .width(148.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onAlbum(a) }
                        ) {
                            Artwork(a.thumb, Modifier.width(148.dp).aspectRatio(1f), RoundedCornerShape(20.dp), px = 400)
                            Text(
                                a.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
                            )
                            Text(
                                a.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun AlbumView(
    vm: PlayerViewModel,
    album: AlbumItem,
    accent: Color,
    bottomPad: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit
) {
    var tracks by remember(album.url) { mutableStateOf<List<Track>?>(null) }
    var error by remember(album.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(album.url) {
        try {
            tracks = ArtistRepository.albumTracks(album)
        } catch (e: Exception) {
            error = e.message ?: "Impossibile caricare l'album"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        item(key = "ahead") {
            Column(Modifier.statusBarsPadding()) {
                BackRow(onBack)
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(album.thumb, Modifier.size(120.dp), RoundedCornerShape(24.dp), px = 400)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            album.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            album.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val list = tracks
                Button(
                    onClick = { list?.let { vm.play(it, 0) } },
                    enabled = !list.isNullOrEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Riproduci")
                }
            }
        }

        val list = tracks
        when {
            error != null -> item(key = "aerr") {
                Text(error ?: "", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list == null -> item(key = "aload") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { BouncyLoader() }
            }
            else -> itemsIndexed(list, key = { i, t -> "al-$i-${t.id}" }) { index, track ->
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
