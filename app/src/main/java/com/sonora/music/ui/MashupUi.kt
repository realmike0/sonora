@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class Piece(val track: Track, startS: Float = 0f, endS: Float = 30f) {
    var start by mutableStateOf(startS)
    var end by mutableStateOf(endS)
}

/**
 * Mashup base: scegli fino a 4 brani, per ognuno il pezzo da usare (inizio e fine), e Sonora li suona
 * uno dopo l'altro. La separazione di voci e base arriverà più avanti.
 */
@Composable
fun MashupSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val pieces = remember { mutableStateListOf<Piece>() }
    var picking by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<Int?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    ModalBottomSheet(onDismissRequest = { job?.cancel(); onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tr("Mashup"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                tr("Scegli fino a 4 brani e, per ognuno, il pezzo da usare."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            pieces.forEachIndexed { i, p ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}. ${p.track.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (running == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { pieces.removeAt(i) }) { Text(tr("Togli")) }
                    }
                    RangeSlider(
                        value = p.start..p.end,
                        onValueChange = { r ->
                            if (r.endInclusive - r.start >= 3f) { p.start = r.start; p.end = r.endInclusive }
                        },
                        valueRange = 0f..360f
                    )
                    Text("${fmt(p.start)} → ${fmt(p.end)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (pieces.size < 4) {
                OutlinedButton(onClick = { picking = true }) { Text(tr("Aggiungi un brano")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = pieces.size >= 2 && running == null,
                    onClick = {
                        job = scope.launch {
                            try {
                                for ((i, p) in pieces.toList().withIndex()) {
                                    running = i
                                    vm.play(listOf(p.track), 0)
                                    var waited = 0
                                    while (vm.durationMs <= 0L && waited < 150) { delay(100); waited++ }
                                    vm.seekToMs((p.start * 1000).toLong())
                                    val endMs = (p.end * 1000).toLong()
                                    while (vm.positionMs < endMs - 150 && vm.nowPlaying?.id == p.track.id) delay(120)
                                }
                            } finally {
                                running = null
                            }
                        }
                    }
                ) { Text(tr("Riproduci mashup")) }
                if (running != null) {
                    OutlinedButton(onClick = { job?.cancel(); vm.togglePlay() }) { Text(tr("Ferma")) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (picking) {
        val all = remember { (vm.store.liked + vm.store.played).distinctBy { it.id } }
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(tr("Chiudi")) } },
            title = { Text(tr("Scegli un brano")) },
            text = {
                Column {
                    OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text(tr("Cerca")) })
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(all.filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) }.take(60), key = { it.id }) { t ->
                            Column(Modifier.fillMaxWidth().clickable {
                                if (pieces.size < 4) pieces.add(Piece(t))
                                picking = false
                            }.padding(vertical = 8.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun fmt(s: Float): String = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)
