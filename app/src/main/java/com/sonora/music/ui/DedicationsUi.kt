@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import com.sonora.music.SonoraId
import com.sonora.music.Track
import kotlinx.coroutines.launch

private val Red = Color(0xFFFF2D4A)

/** Cuore delle dediche, con un bagliore rosso che si muove dietro il tasto. */
@Composable
fun DedicationHeart(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "heart")
    val a by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val pulse by t.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    Box(
        Modifier.size(48.dp).drawBehind {
            val r = size.minDimension * 0.5f
            val c = Offset(center.x + kotlin.math.cos(a) * r * 0.35f, center.y + kotlin.math.sin(a) * r * 0.35f)
            drawCircle(
                Brush.radialGradient(listOf(Red.copy(alpha = 0.55f * pulse), Color.Transparent), center = c, radius = r * 1.5f),
                radius = r * 1.5f, center = c
            )
        },
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.Favorite, contentDescription = tr("Dediche"), tint = Red)
        }
    }
}

/** Dediche di tutti gli utenti Sonora ID: tocca per ascoltare il brano. */
@Composable
fun DedicationsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<SonoraId.Dedication>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload, SonoraId.signedIn) {
        if (!SonoraId.signedIn) return@LaunchedEffect
        error = null
        try {
            list = SonoraId.dedications(c)
        } catch (e: Exception) {
            list = emptyList()
            error = e.message
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(tr("Dediche"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            val found = list
            when {
                !SonoraId.signedIn -> Text(
                    tr("Accedi con Sonora ID nelle impostazioni per vedere e fare dediche."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)
                )
                found == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { BouncyLoader() }
                found.isEmpty() -> Text(
                    error ?: tr("Ancora nessuna dedica"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)
                )
                else -> LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(found, key = { it.id }) { d ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(Red.copy(alpha = 0.12f))
                                .clickable { vm.play(listOf(d.track), 0); onDismiss() }
                                .padding(14.dp)
                        ) {
                            Text("${d.from}  →  ${d.to}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Red)
                            if (d.msg.isNotBlank()) Text(d.msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(d.track.thumb, Modifier.size(44.dp), RoundedCornerShape(10.dp), px = 128)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.track.title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                    Text(d.track.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { if (d.mine) SonoraId.deleteDedication(c, d.id) else SonoraId.reportDedication(c, d.id) }
                                        reload++
                                    }
                                }) { Text(tr(if (d.mine) "Elimina" else "Segnala")) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Scrivi la dedica per il brano scelto. */
@Composable
fun DedicateSheet(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(vm.store.userName) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Dedica a qualcuno"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text("${track.title} · ${track.artist}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (!SonoraId.signedIn) {
                Text(tr("Accedi con Sonora ID nelle impostazioni per fare una dedica."), modifier = Modifier.padding(vertical = 16.dp))
            } else if (!SonoraId.verified) {
                Text(tr("Verifica la tua email nel Profilo per scrivere dediche."), modifier = Modifier.padding(vertical = 16.dp))
            } else {
                OutlinedTextField(to, { to = it.take(30) }, label = { Text(tr("Per chi è")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(msg, { msg = it.take(160) }, label = { Text(tr("Messaggio")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(from, { from = it.take(24) }, label = { Text(tr("Il tuo nome")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    tr("La dedica è visibile a tutti gli utenti Sonora ID. Niente link o contatti."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                info?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !busy && to.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                SonoraId.dedicate(c, track, to, msg, from)
                                onDismiss()
                                vm.dedOpen = true
                            } catch (e: Exception) {
                                info = e.message ?: "Errore"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Pubblica")) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
