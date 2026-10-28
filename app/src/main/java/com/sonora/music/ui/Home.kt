@file:OptIn(ExperimentalFoundationApi::class)

package com.sonora.music.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import com.sonora.music.Shelf
import com.sonora.music.Track

/** Home: "Scelte del giorno" in griglia 3x3, poi gli scaffali come su YouTube Music. */
@Composable
fun HomeScreen(vm: PlayerViewModel, bottomPad: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val home = vm.home
    val recent = vm.store.played.take(15)

    when {
        home == null && vm.homeLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BouncyLoader()
        }

        home == null -> Column(
            modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                vm.homeError ?: "Home non disponibile",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { vm.loadHome(force = true) }) { Text("Riprova") }
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPad + 16.dp)
        ) {
            item(key = "picks-title") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
                    if (vm.store.userName.isNotBlank()) {
                        Text(
                            "Ciao, ${vm.store.userName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Scelte del giorno",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            item(key = "picks-grid") {
                PicksGrid(vm, home.picks)
            }

            item(key = "picks-play") {
                FilledTonalButton(
                    onClick = { vm.play(home.picks, 0) },
                    enabled = home.picks.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Riproduci le scelte")
                }
            }

            if (recent.isNotEmpty()) {
                item(key = "shelf-recent") { ShelfRow(vm, Shelf("Ascoltati di recente", recent)) }
            }

            itemsIndexed(home.shelves, key = { i, s -> "shelf-$i-${s.title}" }) { _, shelf ->
                ShelfRow(vm, shelf)
            }
        }
    }
}

@Composable
private fun PicksGrid(vm: PlayerViewModel, picks: List<Track>) {
    Column(
        Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    val track = picks.getOrNull(index)
                    if (track == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        PickTile(
                            track = track,
                            playing = vm.nowPlaying?.id == track.id,
                            onClick = { vm.play(picks, index) },
                            onLong = { vm.menuTrack = track },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickTile(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(if (playing) 32.dp else 20.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLong()
                }
            )
    ) {
        Artwork(track.thumb, Modifier.fillMaxSize(), shape, px = 400)
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp)
        ) {
            Text(
                track.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShelfRow(vm: PlayerViewModel, shelf: Shelf) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            shelf.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shelf.tracks, key = { it.id }) { track ->
                ShelfCard(
                    track = track,
                    onClick = { vm.play(shelf.tracks, shelf.tracks.indexOf(track)) },
                    onLong = { vm.menuTrack = track }
                )
            }
        }
    }
}

@Composable
private fun ShelfCard(track: Track, onClick: () -> Unit, onLong: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLong()
                }
            )
    ) {
        Artwork(track.thumb, Modifier.width(148.dp).aspectRatio(1f), RoundedCornerShape(20.dp), px = 400)
        Text(
            track.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
