package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.music.ModNotify
import com.sonora.music.SonoraId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun when_(ms: Long) = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))

/** Posta in arrivo: avvisi dell'amministrazione e ticket con l'admin. */
@Composable
fun InboxPage(startNew: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SonoraId.InboxItem>?>(null) }
    var tickets by remember { mutableStateOf<List<SonoraId.TicketRow>>(emptyList()) }
    var openTicket by remember { mutableStateOf<String?>(null) }
    var compose by remember { mutableStateOf(startNew) }
    var reload by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        try {
            items = SonoraId.inbox(context)
            tickets = SonoraId.tickets(context)
            SonoraId.inboxRead(context)
        } catch (e: Exception) { error = e.message; if (items == null) items = emptyList() }
    }

    BackHandler(enabled = openTicket != null) { openTicket = null; reload++ }

    if (!SonoraId.signedIn) {
        Text(tr("Accedi con Sonora ID da Gestisci per usare la posta."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val t = openTicket
    if (t != null) { TicketChat(t) { openTicket = null; reload++ }; return }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { compose = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(tr("Apri un ticket con l'admin")) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (tickets.isNotEmpty()) {
            Text(tr("Ticket"), style = MaterialTheme.typography.titleMedium)
            tickets.forEach { tk ->
                Surface(
                    onClick = { openTicket = tk.id }, shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tk.subject, style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr(if (tk.status == "open") "Aperto" else "Chiuso") + " · " + when_(tk.updated),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Text(tr("Messaggi"), style = MaterialTheme.typography.titleMedium)
        val list = items
        if (list == null) Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { BouncyLoader(40.dp) }
        else if (list.isEmpty()) Text(tr("Nessun messaggio"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else list.forEach { m ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(Color(ModNotify.color(m.kind))))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr(ModNotify.title(m.kind)) + " · " + when_(m.ts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (compose) {
        var subject by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { compose = false },
            title = { Text(tr("Nuovo ticket")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(subject, { subject = it.take(80) }, singleLine = true, label = { Text(tr("Oggetto")) })
                    OutlinedTextField(text, { text = it.take(1000) }, label = { Text(tr("Scrivi qui")) }, minLines = 3)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !sending && subject.length >= 3 && text.length >= 3, onClick = {
                    sending = true; err = null
                    scope.launch {
                        try { openTicket = SonoraId.newTicket(context, subject, text); compose = false }
                        catch (e: Exception) { err = e.message } finally { sending = false }
                    }
                }) { Text(tr("Invia")) }
            },
            dismissButton = { TextButton(onClick = { compose = false }) { Text(tr("Annulla")) } }
        )
    }
}

/** Chat di un ticket con l'admin. Si aggiorna da sola ogni pochi secondi. */
@Composable
private fun TicketChat(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<SonoraId.TicketFull?>(null) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, bump) {
        while (true) {
            data = runCatching { SonoraId.ticket(context, id) }.getOrNull() ?: data
            delay(8000)
        }
    }
    val d = data
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(tr("Indietro")) }
            Text(d?.subject ?: "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        }
        if (d == null) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { BouncyLoader(40.dp) }
        else {
            d.messages.forEach { m ->
                val mine = m.side == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (!mine) Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(m.text)
                            Text(when_(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (d.status == "open") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it.take(1000) }, placeholder = { Text(tr("Scrivi un messaggio…")) }, modifier = Modifier.weight(1f))
                    IconButton(enabled = text.isNotBlank(), onClick = {
                        val t = text; text = ""; err = null
                        scope.launch {
                            try { SonoraId.ticketMsg(context, id, t); bump++ } catch (e: Exception) { err = e.message; text = t }
                        }
                    }) { Icon(Icons.AutoMirrored.Rounded.Send, tr("Invia")) }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else Text(tr("Ticket chiuso"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
