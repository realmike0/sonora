package com.sonora.music.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sonora.music.Framework
import kotlinx.coroutines.launch

/** Propone (una volta) di installare Sonora Framework: Android chiede conferma con un tocco. */
@Composable
fun FrameworkPrompt() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { c.getSharedPreferences("sonora", Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val outdated = remember { Framework.outdated(c) }

    LaunchedEffect(Unit) {
        show = outdated || (!Framework.installed(c) && !prefs.getBoolean("fw_later", false))
    }
    if (!show) return

    AlertDialog(
        onDismissRequest = { if (!busy) show = false },
        title = { Text(tr(if (outdated) "Aggiorna Sonora Framework" else "Installa Sonora Framework")) },
        text = {
            Column {
                Text(tr("È un'app senza icona che gestisce i tuoi account Sonora: password, dispositivi collegati, email e più account. Android ti chiederà di confermare l'installazione."))
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (Framework.installLatest(c) { progress = it }) show = false
                    } catch (e: Exception) {
                        error = e.message ?: "Installazione non riuscita"
                    }
                    busy = false
                }
            }) { Text(tr("Installa")) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                if (!outdated) prefs.edit().putBoolean("fw_later", true).apply()
                show = false
            }) { Text(tr("Più tardi")) }
        }
    )
}
