@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.admin

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

private fun date(ms: Long) = SimpleDateFormat("dd/MM/yy HH:mm", Locale.ITALY).format(Date(ms))

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("admin", 0) }
    var key by remember { mutableStateOf(prefs.getString("key", null)) }
    val k = key
    if (k == null) {
        LoginScreen { prefs.edit().putString("key", it).apply(); key = it }
    } else {
        Main(k, onLogout = { prefs.edit().remove("key").apply(); key = null })
    }
}

@Composable
private fun LoginScreen(onKey: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Sonora Admin", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.height(8.dp))
        Text("Inserisci la chiave amministratore. Resta solo su questo telefono.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.trim() }, singleLine = true, label = { Text("ADMIN_KEY") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = text.length >= 16 && !busy,
            onClick = {
                busy = true; error = null
                scope.launch {
                    try { AdminApi.call(text, "GET", "/v1/admin/users?limit=1"); onKey(text) }
                    catch (e: Exception) { error = e.message }
                    finally { busy = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { if (busy) CircularProgressIndicator(Modifier.height(24.dp)) else Text("Entra") }
    }
}

@Composable
private fun Main(key: String, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var actionUser by remember { mutableStateOf<String?>(null) }
    val titles = listOf("Utenti", "Commenti", "Dediche", "Ticket", "Segnalazioni")
    var openTicket by remember { mutableStateOf<TicketRow?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[tab], fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Aggiorna") }
                    IconButton(onClick = onLogout) { Icon(Icons.Rounded.Logout, "Esci") }
                }
            )
        },
        bottomBar = {
            if (!(tab == 3 && openTicket != null)) NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Rounded.People, null) }, label = { Text("Utenti") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Rounded.Forum, null) }, label = { Text("Commenti") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Rounded.Favorite, null) }, label = { Text("Dediche") })
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Rounded.Chat, null) }, label = { Text("Ticket") })
                NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Rounded.Flag, null) }, label = { Text("Segnal.") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {
            when (tab) {
                0 -> UsersTab(key, reload) { actionUser = it }
                1 -> CommentsTab(key, reload) { actionUser = it }
                2 -> DedsTab(key, reload) { actionUser = it }
                3 -> if (openTicket == null) TicketsTab(key, reload) { openTicket = it } else {
                    androidx.activity.compose.BackHandler { openTicket = null; reload++ }
                    TicketChat(key, openTicket!!, onUser = { actionUser = it }, onBack = { openTicket = null; reload++ })
                }
                else -> ReportsTab(key, reload) { actionUser = it }
            }
        }
    }
    actionUser?.let { id ->
        UserSheet(key, id, onDone = { reload++ }, onDismiss = { actionUser = null })
    }
}

@Composable
private fun <T> Loader(reload: Int, load: suspend () -> List<T>, content: @Composable (List<T>) -> Unit) {
    var items by remember { mutableStateOf<List<T>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload) {
        error = null
        try { items = load() } catch (e: Exception) { error = e.message; if (items == null) items = emptyList() }
    }
    val list = items
    when {
        list == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
        else -> {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (list.isEmpty() && error == null) Text("Niente da mostrare", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            content(list)
        }
    }
}

@Composable
private fun CardRow(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(), onClick = onClick ?: {}, enabled = onClick != null
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() } }
}

@Composable
private fun UsersTab(key: String, reload: Int, onUser: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { q = it.filter { c -> c.isLetterOrDigit() }.lowercase() }, singleLine = true,
            label = { Text("Cerca per id utente") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Loader(reload + q.hashCode(), { AdminApi.users(key, q) }) { list ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { u ->
                    CardRow({ onUser(u.id) }) {
                        Text(u.name.ifBlank { "(senza nome)" } + "  ·  " + u.id.take(8), style = MaterialTheme.typography.titleMedium)
                        Text("Iscritto ${date(u.created)} · ${u.comments} commenti · ${u.deds} dediche", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (u.admin) AssistChip({}, { Text("admin") })
                            if (u.mod > 0) AssistChip({}, { Text("moderatore dal ${date(u.mod)}") })
                            if (u.verified) AssistChip({}, { Text("verificato") })
                            if (u.banned) AssistChip({}, { Text("bannato") })
                            if (u.until > System.currentTimeMillis()) AssistChip({}, { Text("sospeso fino al ${date(u.until)}") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsTab(key: String, reload: Int, onUser: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var bump by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<CommentRow?>(null) }
    Loader(reload + bump, { AdminApi.comments(key) }) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { c ->
                CardRow {
                    Text(c.name + "  ·  " + date(c.ts) + if (c.reports > 0) "  ·  ${c.reports} segnalazioni" else "", style = MaterialTheme.typography.labelLarge, color = if (c.reports > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ confirm = c }) { Text("Rimuovi") }
                        OutlinedButton({ onUser(c.user) }) { Text("Utente") }
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Rimuovere il commento?") }, text = { Text(c.text) },
            confirmButton = {
                TextButton({
                    scope.launch { runCatching { AdminApi.call(key, "POST", "/v1/admin/comment-rm", JSONObject().put("id", c.id)) }; bump++ }
                    confirm = null
                }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun DedsTab(key: String, reload: Int, onUser: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var bump by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<DedRow?>(null) }
    Loader(reload + bump, { AdminApi.deds(key) }) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { d ->
                CardRow {
                    Text("${d.from} → ${d.to}  ·  ${date(d.ts)}" + if (d.hidden) "  ·  nascosta" else "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.title, style = MaterialTheme.typography.titleSmall)
                    if (d.msg.isNotBlank()) Text(d.msg, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ confirm = d }) { Text("Rimuovi") }
                        OutlinedButton({ onUser(d.user) }) { Text("Utente") }
                    }
                }
            }
        }
    }
    confirm?.let { d ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Rimuovere la dedica?") }, text = { Text(d.msg.ifBlank { d.title }) },
            confirmButton = {
                TextButton({
                    scope.launch { runCatching { AdminApi.call(key, "POST", "/v1/admin/purge", JSONObject().put("ded", d.id)) }; bump++ }
                    confirm = null
                }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Annulla") } }
        )
    }
}

/** Azioni su un utente. I pulsanti seguono lo stato: se è bannato vedi "Togli ban", se è sospeso "Togli sospensione", ecc. */
@Composable
private fun UserSheet(key: String, id: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserRow?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var warn by remember { mutableStateOf(false) }
    var warnText by remember { mutableStateOf("") }
    var suspend by remember { mutableStateOf(false) }
    var bump by remember { mutableIntStateOf(0) }
    LaunchedEffect(bump) {
        user = runCatching { AdminApi.users(key, id).firstOrNull { it.id == id } }.getOrNull() ?: user
    }
    fun act(body: JSONObject) {
        if (busy) return
        busy = true
        scope.launch {
            msg = try { AdminApi.call(key, "POST", "/v1/admin/user", body.put("user", id)); onDone(); bump++; "Fatto" } catch (e: Exception) { e.message }
            busy = false
        }
    }
    val u = user
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                (u?.name?.ifBlank { null } ?: "Utente") + " · " + id.take(8),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (u != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (u.admin) AssistChip({}, { Text("admin") })
                    if (u.mod > 0) AssistChip({}, { Text("moderatore") })
                    if (u.banned) AssistChip({}, { Text("bannato") })
                    if (u.until > System.currentTimeMillis()) AssistChip({}, { Text("sospeso fino al ${date(u.until)}") })
                }
            }
            if (u == null) {
                CircularProgressIndicator()
            } else if (warn) {
                OutlinedTextField(warnText, { warnText = it }, label = { Text("Reclamo (lo vede in app e per email)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button({ act(JSONObject().put("action", "warn").put("text", warnText)); warn = false; warnText = "" }, enabled = warnText.length >= 3 && !busy, modifier = Modifier.fillMaxWidth()) { Text("Invia reclamo") }
                OutlinedButton({ warn = false }, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
            } else if (suspend) {
                Text("Per quanti giorni?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 7, 30).forEach { d -> FilledTonalButton({ act(JSONObject().put("action", "suspend").put("days", d)); suspend = false }, enabled = !busy) { Text("$d g") } }
                }
                OutlinedButton({ suspend = false }, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
            } else {
                Button({ warn = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Invia reclamo") }
                if (u.admin) {
                    Text("Questo sei tu: l'admin non si può bannare, sospendere o togliere dai moderatori.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (u.until > System.currentTimeMillis()) {
                        OutlinedButton({ act(JSONObject().put("action", "unsuspend")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Togli sospensione") }
                    } else {
                        FilledTonalButton({ suspend = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Sospendi") }
                    }
                    if (u.mod > 0) {
                        OutlinedButton({ act(JSONObject().put("action", "unmod")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Togli moderatore") }
                    } else {
                        FilledTonalButton({ act(JSONObject().put("action", "mod")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Rendi moderatore") }
                    }
                    if (u.banned) {
                        OutlinedButton({ act(JSONObject().put("action", "unban")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Togli ban") }
                    } else {
                        FilledTonalButton({ act(JSONObject().put("action", "ban")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Banna") }
                    }
                }
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Campo di scrittura da chat: a pillola, cresce fino a 5 righe e con il pulsante di invio tondo. */
@Composable
private fun ChatInput(text: String, onText: (String) -> Unit, placeholder: String, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.TextField(
            value = text, onValueChange = onText, placeholder = { Text(placeholder) },
            maxLines = 5, shape = RoundedCornerShape(26.dp),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.FilledIconButton(onClick = onSend, enabled = text.isNotBlank(), modifier = Modifier.size(52.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Invia")
        }
    }
}

@Composable
private fun TicketsTab(key: String, reload: Int, onOpen: (TicketRow) -> Unit) {
    Loader(reload, { AdminApi.tickets(key) }) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { t ->
                CardRow({ onOpen(t) }) {
                    Text(t.subject + if (t.unread) "  ●" else "", style = MaterialTheme.typography.titleMedium, color = if (t.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(t.last, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Text((if (t.open) "aperto" else "chiuso") + " · " + t.user.take(8) + " · " + date(t.updated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Chat di un ticket: l'utente scrive dall'app, tu rispondi da qui. Il campo resta sopra la tastiera. */
@Composable
private fun TicketChat(key: String, t: TicketRow, onUser: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<List<TicketMsg>>(emptyList()) }
    var user by remember { mutableStateOf(t.user) }
    var text by remember { mutableStateOf("") }
    var bump by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(t.open) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(bump) {
        while (true) {
            runCatching { AdminApi.ticket(key, t.id) }.onSuccess { user = it.first; msgs = it.second }
            kotlinx.coroutines.delay(6000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("Indietro") }
            Text(t.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            OutlinedButton({ onUser(user) }) { Text("Utente") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(msgs.size) { i ->
                val m = msgs[i]
                val mine = m.side == "admin"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 4.dp, bottomEnd = if (mine) 4.dp else 20.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    ) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { Text(m.text); Text(date(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (open) {
            ChatInput(text, { text = it.take(1000) }, "Rispondi…") {
                val send = text.trim(); text = ""
                msgs = msgs + TicketMsg("admin", send, System.currentTimeMillis())
                scope.launch {
                    try { AdminApi.call(key, "POST", "/v1/admin/tickets/${t.id}/msg", JSONObject().put("text", send)); bump++ }
                    catch (e: Exception) { error = e.message; text = send }
                }
            }
            TextButton({
                scope.launch { runCatching { AdminApi.call(key, "POST", "/v1/admin/tickets/${t.id}/close", JSONObject()) }; open = false }
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Chiudi ticket") }
        } else Text("Ticket chiuso", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    }
}

/** Segnalazioni dei moderatori: leggi, e se serve rimuovi il commento o agisci sull'utente. */
@Composable
private fun ReportsTab(key: String, reload: Int, onUser: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var bump by remember { mutableIntStateOf(0) }
    Loader(reload + bump, { AdminApi.reports(key) }) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { r ->
                CardRow {
                    Text("Da ${r.reporter.take(8)} su ${r.target.take(8)} · ${date(r.ts)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.commentText.isNotBlank()) Text("“${r.commentText}”", style = MaterialTheme.typography.bodyMedium)
                    if (r.text.isNotBlank()) Text("Motivo: ${r.text}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({
                            scope.launch { runCatching { AdminApi.call(key, "POST", "/v1/admin/report-resolve", JSONObject().put("id", r.id).put("removeComment", true)) }; bump++ }
                        }) { Text("Rimuovi commento") }
                        OutlinedButton({
                            scope.launch { runCatching { AdminApi.call(key, "POST", "/v1/admin/report-resolve", JSONObject().put("id", r.id)) }; bump++ }
                        }) { Text("Ignora") }
                        OutlinedButton({ onUser(r.target) }) { Text("Utente") }
                    }
                }
            }
        }
    }
}
