package com.sonora.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import com.sonora.music.SonoraId
import kotlinx.coroutines.delay

const val FAV_ID = "__fav"

private fun countdown(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val s = ms / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val hms = "%02d:%02d:%02d".format(h, m, sec)
    return if (d > 0) "${d}g $hms" else hms
}

/** Album in attesa: copertina e conto alla rovescia, come su Spotify. Non compare se non ce ne sono. */
@Composable
fun UpcomingShelf(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<SonoraId.Upcoming>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        items = runCatching { SonoraId.upcoming(context) }.getOrDefault(emptyList())
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    val list = items.filter { it.release > now }
    if (list.isEmpty()) return
    Column(modifier) {
        Text(
            tr("In arrivo"),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { u ->
                Column(Modifier.width(150.dp)) {
                    Box {
                        Artwork(u.cover, Modifier.size(150.dp), RoundedCornerShape(20.dp), px = 400)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        ) {
                            Text(
                                countdown(u.release - now),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(u.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(u.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Pagina "Preferiti" della Raccolta: artisti preferiti e album in arrivo. */
@Composable
fun FavoritesPage(vm: PlayerViewModel, bottomPad: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
    val artists = vm.store.favArtists
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        item {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Indietro")) }
                Text(tr("Artisti preferiti"), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
        if (artists.isEmpty()) {
            item {
                Text(
                    tr("Tocca il cuore nella pagina di un artista per aggiungerlo qui."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        items(artists.toList(), key = { it }) { name ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.artistOpen = name }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(52.dp).clip(CookieShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item { UpcomingShelf(Modifier.fillMaxWidth()) }
    }
}
