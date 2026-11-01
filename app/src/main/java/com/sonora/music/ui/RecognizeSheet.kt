@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.music.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sonora.music.MusicRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.Recognizer
import kotlinx.coroutines.launch

/** "Che brano è?": ascolta 10 secondi dal microfono e propone il brano trovato. */
@Composable
fun RecognizeSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // idle | listening | searching | found | none | error
    var state by remember { mutableStateOf("idle") }
    var result by remember { mutableStateOf<Recognizer.Result?>(null) }
    var message by remember { mutableStateOf("") }

    fun start() {
        state = "listening"
        scope.launch {
            try {
                val wav = Recognizer.record(10)
                state = "searching"
                val r = Recognizer.identify(context, wav)
                if (r == null) state = "none" else { result = r; state = "found" }
            } catch (e: Exception) {
                message = e.message ?: "Errore"
                state = "error"
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else { message = "Serve il permesso del microfono"; state = "error" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                "idle" -> {
                    Text("Che brano è?", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Avvicina il telefono alla musica: ascolto per 10 secondi.", textAlign = TextAlign.Center)
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Ascolta") }
                }
                "listening" -> {
                    BouncyLoader()
                    Text("Sto ascoltando…", style = MaterialTheme.typography.titleMedium)
                }
                "searching" -> {
                    BouncyLoader()
                    Text("Cerco il brano…", style = MaterialTheme.typography.titleMedium)
                }
                "found" -> {
                    val r = result!!
                    Text(r.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                    Text(r.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        state = "searching"
                        scope.launch {
                            val track = runCatching { MusicRepository.search("${r.artist} ${r.title}").firstOrNull() }.getOrNull()
                            if (track != null) {
                                vm.play(listOf(track), 0)
                                onDismiss()
                            } else {
                                message = "Brano non trovato su YouTube Music"
                                state = "error"
                            }
                        }
                    }) { Text("Riproduci") }
                    OutlinedButton(onClick = { state = "idle" }) { Text("Riprova") }
                }
                "none" -> {
                    Text("Non l'ho riconosciuto", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { state = "idle" }) { Text("Riprova") }
                }
                else -> {
                    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = { state = "idle" }) { Text("Riprova") }
                }
            }
        }
    }
}
