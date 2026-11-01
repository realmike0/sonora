package com.sonora.music.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonora.music.Config
import com.sonora.music.Tracking
import kotlinx.coroutines.launch

/** Sceglie come verificarsi: Telegram (numero) oppure email (codice a 6 cifre). */
@Composable
fun TrackingFlow(onDone: () -> Unit) {
    var method by remember { mutableStateOf<String?>(null) }
    when (method) {
        "tg" -> TelegramFlow(onDone, onBack = { method = null })
        "mail" -> EmailFlow(onDone, onBack = { method = null })
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { method = "tg" }, modifier = Modifier.fillMaxWidth()) { Text(tr("Verifica con Telegram")) }
            FilledTonalButton(onClick = { method = "mail" }, modifier = Modifier.fillMaxWidth()) { Text(tr("Verifica con email")) }
        }
    }
}

@Composable
private fun TelegramFlow(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var sid by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(attempt) {
        error = null
        try {
            val id = Tracking.telegramStart(context)
            sid = id
            uri.openUri("https://t.me/${Config.TELEGRAM_BOT}?start=$id")
            Tracking.telegramWait(context, id)
            onDone()
        } catch (e: Exception) {
            error = e.message ?: "Errore"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (error == null) "Si è aperto Telegram: tocca Avvia e condividi il tuo numero con il pulsante. Poi torna qui."
            else error!!,
            color = if (error == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sid?.let { id ->
                OutlinedButton(onClick = { uri.openUri("https://t.me/${Config.TELEGRAM_BOT}?start=$id") }) { Text(tr("Riapri Telegram")) }
            }
            if (error != null) OutlinedButton(onClick = { attempt++ }) { Text(tr("Riprova")) }
            TextButton(onClick = onBack) { Text(tr("Indietro")) }
        }
    }
}

/** Email → codice a 6 cifre → tracciamento attivo. */
@Composable
private fun EmailFlow(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !sent,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        if (sent) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text(tr("Codice a 6 cifre")) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            enabled = !busy && email.contains("@") && (!sent || code.length == 6),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        if (!sent) {
                            Tracking.requestCode(context, email)
                            sent = true
                        } else {
                            Tracking.verify(context, email, code)
                            onDone()
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Errore"
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (!sent) "Invia codice" else "Verifica") }
        TextButton(onClick = onBack) { Text(tr("Indietro")) }
    }
}

/** Dialogo mostrato dopo l'onboarding: il tracciamento è facoltativo e parte solo dopo la verifica. */
@Composable
fun ConsentDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var step by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (step == 0) "Vuoi il tuo Wrapped?" else "Verifica che sei tu") },
        text = {
            if (step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Se accetti, Sonora salva sul nostro server i brani che ascolti (titolo, artista, orario, durata) per creare il tuo Wrapped di ogni anno."))
                    Text(tr("Per verificare che sei tu usiamo Telegram (numero di telefono) oppure l'email: vengono conservati solo come impronta (hash), mai in chiaro. Niente pubblicità, niente vendita dei dati. Puoi smettere e cancellare tutto quando vuoi dalle Impostazioni."))
                    Text(tr("Se rifiuti, nulla lascia il telefono: il Wrapped resta solo locale."))
                    TextButton(onClick = { uri.openUri(Config.PRIVACY_URL) }) { Text(tr("Leggi la privacy policy")) }
                }
            } else {
                TrackingFlow(onDone = onClose)
            }
        },
        confirmButton = {
            if (step == 0) Button(onClick = { step = 1 }) { Text(tr("Accetto")) }
        },
        dismissButton = {
            TextButton(onClick = {
                Tracking.decline(context)
                onClose()
            }) { Text(if (step == 0) "No, grazie" else "Non ora") }
        }
    )
}

/** Sezione Impostazioni: stato, attivazione con verifica, spegnimento, eliminazione dati, indirizzo server. */
@Composable
fun TrackingSection(onWrapped: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var enabling by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(Tracking.serverUrl(context)) }
    var msg by remember { mutableStateOf<String?>(null) }
    val state = Tracking.state

    Text(
        when (state) {
            "accepted" -> "Attivo: i tuoi ascolti vengono salvati sul server per il Wrapped."
            "declined" -> "Spento: nessun ascolto lascia il telefono."
            else -> "Non ancora scelto."
        },
        style = MaterialTheme.typography.bodyMedium
    )
    OutlinedTextField(
        value = url,
        onValueChange = {
            url = it
            Tracking.setServerUrl(context, it)
        },
        label = { Text(tr("Indirizzo server")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (enabling && state != "accepted") {
        TrackingFlow(onDone = { enabling = false })
    } else if (state != "accepted") {
        FilledTonalButton(
            enabled = Tracking.hasServer(context),
            onClick = { enabling = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(tr("Attiva il tracciamento")) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Tracking.disable(context) }) { Text(tr("Spegni")) }
            OutlinedButton(onClick = {
                scope.launch {
                    msg = try {
                        Tracking.deleteServerData(context)
                        "Dati eliminati dal server"
                    } catch (e: Exception) {
                        e.message ?: "Errore"
                    }
                }
            }) { Text(tr("Elimina i miei dati")) }
        }
    }
    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onWrapped) { Text(tr("Apri Wrapped")) }
        TextButton(onClick = { uri.openUri(Config.PRIVACY_URL) }) { Text(tr("Privacy")) }
    }
}
