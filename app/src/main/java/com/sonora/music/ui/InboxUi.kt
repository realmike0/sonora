package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
fun InboxPage(startNew: Boolean, modifier: Modifier = Modifier) {
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
        Text(tr("Accedi con Sonora ID da Gestisci per usare la posta."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(20.dp))
        return
    }
    val t = openTicket
    if (t != null) { TicketChat(t, modifier) { openTicket = null; reload++ }; return }

    Column(modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

/** Chat di un ticket con l'admin: i messaggi scorrono, il campo resta in basso e sale con la tastiera (come WhatsApp). */
@Composable
private fun TicketChat(id: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<SonoraId.TicketFull?>(null) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    LaunchedEffect(id, bump) {
        while (true) {
            data = runCatching { SonoraId.ticket(context, id) }.getOrNull() ?: data
            delay(6000)
        }
    }
    val d = data
    LaunchedEffect(d?.messages?.size) { val n = d?.messages?.size ?: 0; if (n > 0) list.animateScrollToItem(n - 1) }
    Column(modifier.imePadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(tr("Indietro")) }
            Text(d?.subject ?: "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
        }
        if (d == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { BouncyLoader(40.dp) }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                items(d.messages.size) { i ->
                    val m = d.messages[i]
                    val mine = m.side == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 4.dp, bottomEnd = if (mine) 4.dp else 20.dp),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(0.82f)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                if (!mine) Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(m.text)
                                Text(when_(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (d.status == "open") {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextField(
                        value = text, onValueChange = { text = it.take(1000) }, placeholder = { Text(tr("Scrivi un messaggio…")) },
                        maxLines = 5, shape = RoundedCornerShape(26.dp),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.FilledIconButton(
                        enabled = text.isNotBlank(), modifier = Modifier.size(52.dp),
                        onClick = {
                            val t = text.trim(); text = ""; err = null
                            data = d.copy(messages = d.messages + SonoraId.TicketMsg("user", t, System.currentTimeMillis()))
                            scope.launch {
                                try { SonoraId.ticketMsg(context, id, t); bump++ } catch (e: Exception) { err = e.message; text = t }
                            }
                        }
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, tr("Invia")) }
                }
            } else Text(tr("Ticket chiuso"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
        }
    }
}
