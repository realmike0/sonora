@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.sonora.music.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track
import java.util.Locale

@Composable
fun SonoraScreen(vm: PlayerViewModel) {
    var showPlayer by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (vm.nowPlaying != null) MiniPlayer(vm, onOpen = { showPlayer = true })
        }
    ) { padding ->
        SearchContent(vm, Modifier.padding(padding))
    }

    if (showPlayer && vm.nowPlaying != null) {
        PlayerSheet(vm, onDismiss = { showPlayer = false })
    }
}

// ───────────────────────── Ricerca ─────────────────────────

@Composable
private fun SearchContent(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Text(
            text = "Sonora",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp)
        )
        SearchField(vm)

        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            vm.results.isEmpty() -> Message(
                vm.error ?: "Cerca un brano o un artista per iniziare"
            )

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(vm.results, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        selected = vm.nowPlaying?.id == track.id,
                        onClick = { vm.playFrom(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(vm: PlayerViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = vm.query,
        onValueChange = { vm.query = it },
        placeholder = { Text("Cerca brani, artisti…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (vm.query.isNotEmpty()) {
            {
                IconButton(onClick = { vm.query = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancella")
                }
            }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            vm.search()
            keyboard?.hide()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.thumb, Modifier.size(56.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.durationSec > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(track.durationSec * 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────── Player ─────────────────────────

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val np = vm.nowPlaying ?: return
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(np.artwork, Modifier.size(52.dp), RoundedCornerShape(16.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        np.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        np.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayPauseButton(vm, large = false)
            }
            val playing = vm.wantsPlay
            LinearWavyProgressIndicator(
                progress = { vm.progress },
                amplitude = { if (playing) 1f else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun PlayerSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val np = vm.nowPlaying ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Artwork(
                np.artwork,
                Modifier.fillMaxWidth().aspectRatio(1f),
                RoundedCornerShape(40.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                np.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                np.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))

            var dragging by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = dragging ?: vm.progress,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let(vm::seekToFraction)
                    dragging = null
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val shown = ((dragging ?: vm.progress) * vm.durationMs).toLong()
                Text(formatTime(shown), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(vm.durationMs), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = vm::previous,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, "Precedente", Modifier.size(32.dp))
                }
                PlayPauseButton(vm, large = true)
                FilledTonalIconButton(
                    onClick = vm::next,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Pulsante play/pausa con forma "expressive" che si deforma alla pressione. */
@Composable
private fun PlayPauseButton(vm: PlayerViewModel, large: Boolean) {
    val size = if (large) IconButtonDefaults.largeContainerSize() else IconButtonDefaults.mediumContainerSize()
    FilledIconButton(
        onClick = vm::togglePlay,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = if (vm.wantsPlay) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (vm.wantsPlay) "Pausa" else "Riproduci",
            modifier = Modifier.size(if (large) 40.dp else 28.dp)
        )
    }
}

@Composable
private fun Artwork(url: String?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}
