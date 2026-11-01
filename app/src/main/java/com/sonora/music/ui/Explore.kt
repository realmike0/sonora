package com.sonora.music.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonora.music.ArtistRepository
import com.sonora.music.PlayLog
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track

private data class ArtistStat(val name: String, val count: Int, val fallbackThumb: String?)
private data class SongStat(val track: Track, val count: Int)

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** Esplora: statistiche degli ascolti dell'ultima settimana (calcolate sul telefono). */
@Composable
fun ExploreScreen(vm: PlayerViewModel, bottomPad: Dp, modifier: Modifier = Modifier) {
    // Si ricalcola quando arriva un nuovo ascolto.
    val events = remember(vm.nowPlaying?.id) { PlayLog.all() }
    val week = remember(events) {
        val from = System.currentTimeMillis() - WEEK_MS
        events.filter { it.ts >= from && it.ms >= 15_000 }
    }
    val songs = remember(week) {
        week.groupBy { it.id }.map { (id, list) ->
            val e = list.last()
            SongStat(Track(id, e.title, e.artist, e.thumb, 0L), list.size)
        }.sortedByDescending { it.count }.take(30)
    }
    val artists = remember(week) {
        val map = HashMap<String, Int>()
        val thumbs = HashMap<String, String?>()
        week.forEach { e ->
            ArtistRepository.splitArtists(e.artist).forEach { a ->
                map[a] = (map[a] ?: 0) + 1
                if (thumbs[a] == null) thumbs[a] = e.thumb
            }
        }
        map.entries.sortedByDescending { it.value }.take(12).map { ArtistStat(it.key, it.value, thumbs[it.key]) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPad + 16.dp)
    ) {
        item {
            Column(Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)) {
                Text(
                    tr("Esplora"),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    tr("Ultimi 7 giorni"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (songs.isEmpty()) {
            item { Message(tr("Ascolta qualche brano e qui vedrai le tue statistiche")) }
            return@LazyColumn
        }

        // ── Artisti: il primo in grande, gli altri in fila ──
        item { SectionTitle(tr("Artisti")) }
        item {
            val top = artists.first()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable { vm.artistOpen = top.name },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtistCookie(top, Modifier.size(150.dp), vm)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Top 1", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        top.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${top.count} ${tr(if (top.count == 1) "ascolto" else "ascolti")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (artists.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(artists.drop(1), key = { it.name }) { a ->
                        Column(
                            Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).clickable { vm.artistOpen = a.name },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ArtistCookie(a, Modifier.size(84.dp), vm)
                            Text(
                                a.name, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                "${a.count}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── Brani: copertina rettangolare del Top 1 e classifica ──
        item { SectionTitle(tr("Brani")) }
        item {
            val top = songs.first()
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { vm.play(songs.map { it.track }, 0) }
            ) {
                Artwork(top.track.thumb, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), px = 800)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text("Top 1", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                    Text(
                        top.track.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${top.track.artist} · ${top.count} ${tr(if (top.count == 1) "ascolto" else "ascolti")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        itemsIndexed(songs.drop(1), key = { _, s -> "ex-" + s.track.id }) { i, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.play(songs.map { it.track }, i + 1) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 2}", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp)
                )
                Artwork(s.track.thumb, Modifier.size(52.dp), RoundedCornerShape(14.dp), px = 160)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.track.artist, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${s.count} ${tr(if (s.count == 1) "ascolto" else "ascolti")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
    )
}

/** Foto dell'artista dentro il cerchio ondulato; se non c'è ancora usa la copertina di un suo brano. */
@Composable
private fun ArtistCookie(a: ArtistStat, modifier: Modifier, vm: PlayerViewModel) {
    var image by remember(a.name) { mutableStateOf<String?>(null) }
    LaunchedEffect(a.name) {
        image = runCatching { ArtistRepository.load(a.name).image }.getOrNull()
    }
    Artwork(image ?: a.fallbackThumb, modifier, CookieShape, px = 400)
}
