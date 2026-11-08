package com.sonora.framework

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun FwTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val cs: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = cs,
        shapes = Shapes(
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}

private sealed interface Screen {
    data object Accounts : Screen
    data object Add : Screen
    data class Detail(val email: String) : Screen
    data class Password(val email: String) : Screen
    data class Devices(val email: String) : Screen
    data class ChangeEmail(val email: String) : Screen
}

@Composable
fun FwApp(startWithAdd: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val stack = remember {
        mutableStateListOf<Screen>(Screen.Accounts).also { if (startWithAdd) it.add(Screen.Add) }
    }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    fun reset(vararg s: Screen) { stack.clear(); stack.addAll(s.toList()); tick++ }
    fun expired(email: String) {
        store.remove(email)
        Toast.makeText(ctx, "Sessione scaduta: accedi di nuovo", Toast.LENGTH_LONG).show()
        reset(Screen.Accounts)
    }

    BackHandler(stack.size > 1) { pop() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = stack.size to stack.last(),
            transitionSpec = {
                // Entrata da destra verso sinistra, senza rimbalzi (come Android 14+).
                val forward = targetState.first >= initialState.first
                val d = tween<androidx.compose.ui.unit.IntOffset>(320, easing = FastOutSlowInEasing)
                if (forward) (slideInHorizontally(d) { it / 3 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(d) { -it / 6 } + fadeOut(tween(160)))
                else (slideInHorizontally(d) { -it / 6 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(d) { it / 3 } + fadeOut(tween(160)))
            },
            label = "fw-nav"
        ) { (_, screen) ->
            when (screen) {
                Screen.Accounts -> AccountsScreen(store, tick, onAdd = { push(Screen.Add) }, onOpen = { push(Screen.Detail(it)) }, onClose = onClose, onChanged = { tick++ })
                Screen.Add -> AddScreen(store, onBack = { pop() }, onDone = { reset(Screen.Accounts) })
                is Screen.Detail -> DetailScreen(
                    store, screen.email, tick, onBack = { pop() },
                    onPassword = { push(Screen.Password(screen.email)) },
                    onDevices = { push(Screen.Devices(screen.email)) },
                    onEmail = { push(Screen.ChangeEmail(screen.email)) },
                    onExpired = { expired(screen.email) },
                    onChanged = { tick++ },
                    onGone = { reset(Screen.Accounts) }
                )
                is Screen.Password -> PasswordScreen(store, screen.email, onBack = { pop() }, onExpired = { expired(screen.email) })
                is Screen.Devices -> DevicesScreen(store, screen.email, onBack = { pop() }, onExpired = { expired(screen.email) })
                is Screen.ChangeEmail -> ChangeEmailScreen(
                    store, screen.email, onBack = { pop() }, onExpired = { expired(screen.email) },
                    onDone = { new -> reset(Screen.Accounts, Screen.Detail(new)) }
                )
            }
        }
    }
}

// ───────── Componenti ─────────

@Composable
private fun Page(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
        } else Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Avatar(store: Store, email: String, size: Int, version: Int = 0, modifier: Modifier = Modifier) {
    val file = store.avatarFile(email)
    val bmp = remember(email, version, file.lastModified()) {
        if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
    }
    Box(
        modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(email.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size / 2.4f).sp0()), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}

private fun Float.sp0() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, number: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (number) KeyboardType.Number else KeyboardType.Email),
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Msg(text: String?, error: Boolean = true) {
    if (text != null) Text(text, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Busy(on: Boolean) {
    if (on) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Esegue una chiamata mostrando errori; se la sessione è scaduta chiama onExpired. */
private fun CoroutineScope.go(setBusy: (Boolean) -> Unit, setMsg: (String?) -> Unit, onExpired: () -> Unit = {}, block: suspend () -> Unit) {
    launch {
        setBusy(true); setMsg(null)
        try { block() } catch (e: AuthExpired) { onExpired() } catch (e: Exception) { setMsg(e.message ?: "Errore") }
        setBusy(false)
    }
}

// ───────── Schermate ─────────

@Composable
private fun AccountsScreen(store: Store, tick: Int, onAdd: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit, onChanged: () -> Unit) {
    val accounts = remember(tick) { store.list() }
    val active = remember(tick) { store.active()?.email }
    Page("Account Sonora", onBack = onClose) {
        if (accounts.isEmpty()) Text("Nessun account. Aggiungine uno per sincronizzare i tuoi preferiti.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        accounts.forEach { a ->
            Surface(
                onClick = { onOpen(a.email) }, shape = MaterialTheme.shapes.extraLarge,
                color = if (a.email == active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(store, a.email, 52, tick)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            if (a.email == active) "Account in uso" + if (!a.verified) " · email da verificare" else "" else "Tocca per gestire",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (a.email != active) FilledTonalButton(onClick = { store.setActive(a.email); onChanged() }) { Text("Usa") }
                }
            }
        }
        ActionCard(Icons.Rounded.PersonAdd, "Aggiungi account", "Accedi o crea un nuovo Sonora ID", onClick = onAdd)
    }
}

@Composable
private fun AddScreen(store: Store, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Page(if (register) "Crea account" else "Accedi", onBack) {
        Field(email, { email = it.trim() }, "Email")
        Field(pw, { pw = it }, "Password", password = true)
        if (register) Text("Ti mandiamo un codice a 6 cifre per verificare l'email. La email è salvata cifrata. Non ci sono password in chiaro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Msg(msg)
        Busy(busy)
        Button(
            enabled = !busy && email.contains("@") && pw.length >= 8,
            onClick = {
                scope.go({ busy = it }, { msg = it }) {
                    val r = Api.call(ctx, "POST", if (register) "/v1/id/register" else "/v1/id/login", JSONObject().put("email", email).put("password", pw))
                    store.add(email.lowercase(), r.optString("token"), r.optBoolean("verified", false))
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (register) "Crea account" else "Accedi") }
        TextButton(onClick = { register = !register; msg = null }, modifier = Modifier.fillMaxWidth()) {
            Text(if (register) "Ho già un account" else "Non ho un account: creane uno")
        }
    }
}

@Composable
private fun DetailScreen(
    store: Store, email: String, tick: Int, onBack: () -> Unit, onPassword: () -> Unit, onDevices: () -> Unit,
    onEmail: () -> Unit, onExpired: () -> Unit, onChanged: () -> Unit, onGone: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = remember(tick) { store.get(email) }
    var ver by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.go({ busy = it }, { msg = it }) {
            withContext(Dispatchers.IO) { saveAvatar(ctx, uri, store.avatarFile(email)) }
            ver++; onChanged()
        }
    }
    if (acc == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onGone() }; return }
    Page("Account", onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Avatar(store, email, 112, ver + tick)
            }
            Spacer(Modifier.height(10.dp))
            Text(email, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Cambia immagine profilo") }
        }
        if (!acc.verified) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Verifica la tua email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Inserisci il codice a 6 cifre che ti abbiamo mandato. Serve per scrivere dediche.", style = MaterialTheme.typography.bodySmall)
                    Field(code, { code = it.filter(Char::isDigit).take(6) }, "Codice a 6 cifre", number = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = !busy && code.length == 6, onClick = {
                            scope.go({ busy = it }, { msg = it }, onExpired) {
                                Api.call(ctx, "POST", "/v1/id/verify", JSONObject().put("code", code), acc.token)
                                store.setVerified(email, true); code = ""; onChanged()
                            }
                        }) { Text("Verifica") }
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.go({ busy = it }, { msg = it }, onExpired) {
                                Api.call(ctx, "POST", "/v1/id/resend", JSONObject(), acc.token)
                                msg = "Codice inviato"
                            }
                        }) { Text("Invia di nuovo") }
                    }
                }
            }
        }
        Msg(msg, error = msg != "Codice inviato")
        Busy(busy)
        if (store.active()?.email != email) {
            ActionCard(Icons.Rounded.PersonAdd, "Usa questo account", "Sonora userà questo account", onClick = { store.setActive(email); onChanged() })
        }
        ActionCard(Icons.Rounded.Lock, "Cambia password", "Gli altri dispositivi verranno scollegati", onClick = onPassword)
        ActionCard(Icons.Rounded.Devices, "Dispositivi collegati", "Vedi dove hai fatto l'accesso", onClick = onDevices)
        ActionCard(Icons.Rounded.Email, "Cambia email", "Con codice di conferma", onClick = onEmail)
        ActionCard(Icons.Rounded.Logout, "Esci da questo account", "Resta salvato sul server", danger = true, onClick = { confirmLogout = true })
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Uscire da $email?") },
        text = { Text("L'account verrà tolto da questo telefono. I tuoi dati restano sul server e puoi rientrare quando vuoi.") },
        confirmButton = {
            TextButton(onClick = {
                confirmLogout = false
                scope.launch {
                    runCatching { Api.call(ctx, "POST", "/v1/id/logout", JSONObject(), acc.token) }
                    store.remove(email); onGone()
                }
            }) { Text("Esci") }
        },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Annulla") } }
    )
}

@Composable
private fun PasswordScreen(store: Store, email: String, onBack: () -> Unit, onExpired: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = store.get(email)
    if (acc == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    Page("Cambia password", onBack) {
        Field(old, { old = it }, "Password attuale", password = true)
        Field(new, { new = it }, "Nuova password (almeno 8 caratteri)", password = true)
        Field(again, { again = it }, "Ripeti la nuova password", password = true)
        Msg(msg, error = !ok)
        Busy(busy)
        Button(
            enabled = !busy && old.isNotEmpty() && new.length >= 8 && new == again,
            onClick = {
                scope.go({ busy = it }, { msg = it; ok = false }, onExpired) {
                    Api.call(ctx, "POST", "/v1/id/password", JSONObject().put("old", old).put("new", new), acc.token)
                    ok = true; msg = "Password cambiata. Gli altri dispositivi sono stati scollegati."
                    old = ""; new = ""; again = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Cambia password") }
    }
}

@Composable
private fun DevicesScreen(store: Store, email: String, onBack: () -> Unit, onExpired: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = store.get(email)
    if (acc == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var askFor by remember { mutableStateOf<String?>(null) } // id del dispositivo, oppure "*" per tutti gli altri
    var pw by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(reload) {
        scope.go({ busy = it }, { msg = it }, onExpired) {
            val r = Api.call(ctx, "GET", "/v1/id/devices", null, acc.token)
            val a = r.optJSONArray("items")
            items = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
        }
    }
    Page("Dispositivi collegati", onBack) {
        Msg(msg)
        Busy(busy)
        items.forEach { d ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.optString("name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val place = d.optString("place").ifEmpty { "Posizione sconosciuta" }
                        Text("$place · ${d.optString("ip")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (d.optBoolean("current")) "Questo dispositivo" else "Ultimo accesso: " + ago(d.optLong("last")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!d.optBoolean("current")) TextButton(onClick = { askFor = d.optString("id"); pw = "" }) { Text("Scollega") }
                }
            }
        }
        if (items.count { !it.optBoolean("current") } > 1) {
            OutlinedButton(onClick = { askFor = "*"; pw = "" }, modifier = Modifier.fillMaxWidth()) { Text("Scollega tutti gli altri") }
        }
    }
    askFor?.let { target ->
        AlertDialog(
            onDismissRequest = { askFor = null },
            title = { Text("Conferma con la password") },
            text = { Field(pw, { pw = it }, "Password", password = true) },
            confirmButton = {
                TextButton(enabled = pw.isNotEmpty(), onClick = {
                    val body = JSONObject().put("password", pw)
                    if (target == "*") body.put("all", true) else body.put("id", target)
                    askFor = null
                    scope.go({ busy = it }, { msg = it }, onExpired) {
                        Api.call(ctx, "POST", "/v1/id/devices/revoke", body, acc.token)
                        reload++
                    }
                }) { Text("Scollega") }
            },
            dismissButton = { TextButton(onClick = { askFor = null }) { Text("Annulla") } }
        )
    }
}

private fun ago(ts: Long): String {
    val m = ((System.currentTimeMillis() - ts) / 60000).coerceAtLeast(0)
    return when {
        m < 2 -> "adesso"
        m < 60 -> "$m minuti fa"
        m < 60 * 24 -> "${m / 60} ore fa"
        else -> "${m / (60 * 24)} giorni fa"
    }
}

@Composable
private fun ChangeEmailScreen(store: Store, email: String, onBack: () -> Unit, onExpired: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = store.get(email)
    if (acc == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    var step by remember { mutableIntStateOf(1) }
    var newEmail by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Page("Cambia email", onBack) {
        if (step == 1) {
            Field(newEmail, { newEmail = it.trim() }, "Nuova email")
            Field(pw, { pw = it }, "Password", password = true)
            Msg(msg); Busy(busy)
            Button(
                enabled = !busy && newEmail.contains("@") && pw.isNotEmpty(),
                onClick = {
                    scope.go({ busy = it }, { msg = it }, onExpired) {
                        Api.call(ctx, "POST", "/v1/id/email/start", JSONObject().put("email", newEmail).put("password", pw), acc.token)
                        step = 2
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Invia il codice") }
        } else {
            Text("Abbiamo mandato un codice a 6 cifre a $newEmail.", style = MaterialTheme.typography.bodyMedium)
            Field(code, { code = it.filter(Char::isDigit).take(6) }, "Codice a 6 cifre", number = true)
            Msg(msg); Busy(busy)
            Button(
                enabled = !busy && code.length == 6,
                onClick = {
                    scope.go({ busy = it }, { msg = it }, onExpired) {
                        val r = Api.call(ctx, "POST", "/v1/id/email/confirm", JSONObject().put("code", code), acc.token)
                        val n = r.optString("email").ifEmpty { newEmail.lowercase() }
                        store.rename(email, n)
                        onDone(n)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Conferma") }
        }
    }
}

/** Ritaglia al centro in quadrato e salva a 384 px. */
private fun saveAvatar(ctx: android.content.Context, uri: Uri, out: File) {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / (sample * 2) >= 768 && opts.outHeight / (sample * 2) >= 768) sample *= 2
    val src = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw java.io.IOException("Immagine non leggibile")
    val side = minOf(src.width, src.height)
    val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(sq, 384, 384, true)
    out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
