package com.sonora.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sonora.music.Config
import com.sonora.music.PlayerViewModel
import com.sonora.music.SonoraId
import kotlinx.coroutines.launch

/** Sezione "Sonora ID" dentro il Profilo. */
@Composable
fun SonoraIdSection(vm: PlayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) } // "register" | "login"
    var confirmDelete by remember { mutableStateOf(false) }

    if (!SonoraId.signedIn) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { dialog = "register" }) { Text(tr("Crea Sonora ID")) }
            OutlinedButton(onClick = { dialog = "login" }) { Text(tr("Accedi")) }
        }
    } else {
        Text("Sonora ID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(SonoraId.email.orEmpty(), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { scope.launch { SonoraId.sync(context, vm.store) } },
                enabled = !SonoraId.syncing
            ) { Text(tr("Sincronizza ora")) }
            OutlinedButton(onClick = { scope.launch { SonoraId.logout(context) } }) { Text(tr("Esci")) }
        }
        if (com.sonora.music.Framework.installed(context)) {
            Button(onClick = { com.sonora.music.Framework.manage(context) }) { Text(tr("Gestisci")) }
        } else {
            OutlinedButton(onClick = {
                scope.launch { runCatching { com.sonora.music.Framework.installLatest(context) }.onFailure { SonoraId.message = it.message } }
            }) { Text(tr("Installa Sonora Framework")) }
        }
        if (!SonoraId.verified) VerifyEmail()
        SonoraId.message?.let {
            Text(tr(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { confirmDelete = true }) { Text(tr("Elimina Sonora ID"), color = MaterialTheme.colorScheme.error) }
    }

    androidx.compose.runtime.LaunchedEffect(SonoraId.signedIn) { SonoraId.refreshStatus(context) }

    dialog?.let { mode ->
        SonoraIdDialog(vm, mode, onDismiss = { dialog = null })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Eliminare il Sonora ID?")) },
            text = { Text(tr("L'account e i preferiti sul server verranno cancellati. Sul telefono resta tutto.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { runCatching { SonoraId.delete(context) } }
                }) { Text(tr("Elimina")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Annulla")) } }
        )
    }
}

/** Codice a 6 cifre ricevuto per email: serve per scrivere dediche. */
@Composable
private fun VerifyEmail() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text(tr("Verifica la tua email"), style = MaterialTheme.typography.titleSmall)
    Text(
        tr("Ti abbiamo mandato un codice a 6 cifre. Serve per scrivere dediche."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
        label = { Text(tr("Codice a 6 cifre")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = !busy && code.length == 6, onClick = {
            busy = true
            scope.launch {
                info = try { SonoraId.verifyEmail(context, code); null } catch (e: Exception) { e.message }
                busy = false
            }
        }) { Text(tr("Verifica")) }
        OutlinedButton(enabled = !busy, onClick = {
            scope.launch {
                info = try { SonoraId.resendCode(context); tr("Codice inviato") } catch (e: Exception) { e.message }
            }
        }) { Text(tr("Invia di nuovo")) }
    }
    info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun SonoraIdDialog(vm: PlayerViewModel, mode: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val register = mode == "register"

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr(if (register) "Crea Sonora ID" else "Accedi")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (register) {
                    Text(
                        tr("Il Sonora ID salva sul server la tua email (cifrata, per verificarla e recuperare l'account), la password protetta e i tuoi brani preferiti per sincronizzarli. Ti mandiamo un codice per verificare l'email. Puoi eliminare tutto quando vuoi."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                        Text(
                            tr("Accetto la privacy policy"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = { uriHandler.openUri(Config.PRIVACY_URL) }) { Text(tr("Leggi la privacy policy")) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && email.isNotBlank() && password.length >= 8 && (!register || accepted),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            if (register) SonoraId.register(context, email, password) else SonoraId.login(context, email, password)
                            SonoraId.sync(context, vm.store)
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message ?: "Errore"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(tr(if (busy) "Attendi…" else if (register) "Crea" else "Accedi")) }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text(tr("Annulla")) } }
    )
}
