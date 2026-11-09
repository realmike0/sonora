@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.music.Comment
import com.sonora.music.Comments
import com.sonora.music.PlayerViewModel
import kotlinx.coroutines.launch

/** Commenti del brano in riproduzione, con la possibilità di scriverne uno se hai collegato Google. */
@Composable
fun CommentsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val np = vm.nowPlaying ?: return
    val scope = rememberCoroutineScope()
    var list by remember(np.id) { mutableStateOf<List<Comment>?>(null) }
    var error by remember(np.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val cookie = vm.store.googleCookie

    LaunchedEffect(np.id, reload) {
        error = null
        list = null
        try {
            list = Comments.load(np.id)
        } catch (e: Exception) {
            list = emptyList()
            error = e.message ?: "Commenti non disponibili"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(tr("Commenti"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Sonora") })
                androidx.compose.material3.FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("YouTube") })
            }
            Spacer(Modifier.height(8.dp))
            if (tab == 0) SonoraComments(vm, np.id) else {

            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)) {
                val items = list
                when {
                    items == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { BouncyLoader() }
                    items.isEmpty() -> Text(
                        error ?: tr("Nessun commento"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(items, key = { i, _ -> i }) { _, c ->
                            Row(verticalAlignment = Alignment.Top) {
                                Artwork(c.avatar, Modifier.size(36.dp), CircleShape, px = 96)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.author + if (c.date.isNotBlank()) " · ${c.date}" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(c.text, style = MaterialTheme.typography.bodyMedium)
                                    if (c.likes > 0) {
                                        Text(
                                            "♥ ${c.likes}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (cookie == null) {
                Text(
                    tr("Collega l'account Google nelle impostazioni per commentare."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = { Text(tr("Aggiungi un commento…")) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = !sending && text.isNotBlank(),
                        onClick = {
                            sending = true
                            info = null
                            scope.launch {
                                try {
                                    Comments.post(cookie, np.id, text)
                                    text = ""
                                    info = "Commento pubblicato"
                                    reload++
                                } catch (e: Exception) {
                                    info = e.message ?: "Errore"
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, tr("Invia")) }
                }
                info?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Commenti di Sonora: servono un Sonora ID verificato per scrivere; l'AI controlla ogni commento prima di pubblicarlo. */
@Composable
private fun SonoraComments(vm: PlayerViewModel, trackId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(trackId) { mutableStateOf<List<com.sonora.music.SonoraId.SonoraComment>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(trackId, reload) {
        list = runCatching { com.sonora.music.SonoraId.comments(context, trackId) }.getOrDefault(emptyList())
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)) {
        val items = list
        when {
            items == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { BouncyLoader() }
            items.isEmpty() -> Text(tr("Nessun commento: scrivi il primo!"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(items, key = { _, c -> c.id }) { _, c ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(36.dp).androidx_clip().background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(c.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    if (c.mine) com.sonora.music.SonoraId.deleteComment(context, c.id)
                                    else com.sonora.music.SonoraId.reportComment(context, c.id)
                                }
                                info = if (c.mine) null else "Segnalato, grazie"
                                reload++
                            }
                        }) { Text(tr(if (c.mine) "Elimina" else "Segnala"), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    when {
        !com.sonora.music.SonoraId.signedIn -> Text(tr("Accedi con Sonora ID nelle impostazioni per commentare."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        !com.sonora.music.SonoraId.verified -> Text(tr("Verifica la tua email per commentare."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(280) },
                    placeholder = { Text(tr("Scrivi un commento…")) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    enabled = !sending && text.isNotBlank(),
                    onClick = {
                        sending = true; info = null
                        scope.launch {
                            try {
                                com.sonora.music.SonoraId.postComment(context, trackId, vm.store.userName.ifBlank { "Anonimo" }, text)
                                text = ""; reload++
                            } catch (e: Exception) {
                                info = e.message ?: "Errore"
                            } finally { sending = false }
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Rounded.Send, tr("Invia")) }
            }
            info?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun Modifier.androidx_clip(): Modifier = this.clip(CircleShape)
