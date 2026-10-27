package com.sonora.music.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilterChip
import com.sonora.music.AudioStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sonora.music.LyricsRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.Store
import com.sonora.music.SyncState

@OptIn(ExperimentalLayoutApi::class, androidx.media3.common.util.UnstableApi::class)
@Composable
fun SettingsScreen(vm: PlayerViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val store = vm.store
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    // Schermata sempre scura con i colori dinamici del telefono: testo chiaro e leggibile.
    val scheme = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else darkColorScheme()
    }

    MaterialTheme(colorScheme = scheme) {
    Surface(
        color = scheme.background,
        contentColor = scheme.onBackground,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
                }
                Text(
                    "Impostazioni",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            }

            SettingsCard("Profilo") {
                Text(store.userName.ifBlank { "Ospite" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Account locale: Mi piace e playlist sono salvati su questo telefono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard("Testi") {
                LyricsSourcePicker(store)
            }

            SettingsCard("Account Google") {
                GoogleSection(vm)
            }

            SettingsCard("Memoria e cache") {
                CacheSection()
            }

            SettingsCard("Community") {
                Text(
                    "Novità, aggiornamenti e supporto sul canale Telegram di Sonora.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { uriHandler.openUri("https://t.me/sonoraclient") }) {
                    Text("Apri su Telegram")
                }
                Text(
                    "t.me/sonoraclient",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard("Informazioni") {
                Text("Sonora $version", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Versione beta. I brani ascoltati vengono salvati in cache; quelli scaricati restano offline nella Raccolta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Filigrana: in fondo alla pagina, scorre insieme al resto (non resta sovrapposta).
            Watermark(Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
    }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun LyricsSourcePicker(store: Store) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Fonte predefinita", style = MaterialTheme.typography.labelMedium)
                    Text(store.lyricsSource, style = MaterialTheme.typography.titleMedium)
                }
                Text("▾", style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LyricsRepository.sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source) },
                    onClick = {
                        store.updateLyricsSource(source)
                        open = false
                    }
                )
            }
        }
    }
    Text(
        "Per ora è disponibile solo LRCLIB.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GoogleSection(vm: PlayerViewModel) {
    val store = vm.store

    if (store.googleCookie == null) {
        Text(
            "Collega il tuo account per importare tutte le playlist di YouTube Music.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { vm.loginOpen = true }) { Text("Collega account Google") }
        return
    }

    Text("Account collegato", style = MaterialTheme.typography.titleMedium)

    when (vm.syncState) {
        SyncState.Running -> {
            Text(
                vm.syncStatus.ifBlank { "Sincronizzazione in corso…" },
                style = MaterialTheme.typography.bodyMedium
            )
            BouncyLoader()
        }

        SyncState.Failed -> Text(
            vm.syncError ?: "Sincronizzazione non riuscita",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        SyncState.Done -> Text(
            "Playlist sincronizzate",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SyncState.Idle -> Unit
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { vm.syncGoogle() }, enabled = vm.syncState != SyncState.Running) {
            Text("Sincronizza ora")
        }
        OutlinedButton(onClick = { vm.disconnectGoogle() }) { Text("Scollega") }
    }
}

/** Filigrana in basso a sinistra: "GitHub" apre la pagina di realmike0. */
@Composable
private fun Watermark(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Made with ❤️ by realmike0 on",
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "GitHub",
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            textDecoration = TextDecoration.Underline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://www.github.com/realmike0") }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.media3.common.util.UnstableApi::class)
@Composable
private fun CacheSection() {
    val limit = AudioStore.cacheLimitGb
    var confirmClear by remember { mutableStateOf(false) }

    Text(
        "I brani che ascolti vengono salvati in automatico. Quando si supera il limite, " +
            "vengono eliminati prima i meno recenti. I brani scaricati non vengono mai toccati.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val limitText = if (limit <= 0) "illimitata" else "$limit GB"
    Text(
        "In cache: ${formatBytes(AudioStore.cacheUsedBytes)} (limite $limitText)",
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        "Brani completi: ${AudioStore.cachedIds.size} · parziali: ${AudioStore.partialCount}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AudioStore.lastFillError?.let {
        Text(
            "Ultimo errore di salvataggio: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Text(
        "I file sono nella memoria privata dell'app (non visibili con il gestore file): " +
            "per vedere i brani apri Raccolta → In cache.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "Scaricati: ${formatBytes(AudioStore.downloadsUsedBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(1 to "1 GB", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB", 0 to "Illimitata").forEach { (gb, label) ->
            FilterChip(
                selected = limit == gb,
                onClick = { AudioStore.setCacheLimit(gb) },
                label = { Text(label) }
            )
        }
    }

    OutlinedButton(onClick = { confirmClear = true }, enabled = AudioStore.cacheUsedBytes > 0) {
        Text("Svuota cache")
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Svuotare la cache?") },
            text = { Text("I brani in cache verranno eliminati. I brani scaricati restano.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmClear = false
                    AudioStore.clearCache()
                }) { Text("Svuota") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Annulla") }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.getDefault(), "%d MB", bytes / (1024 * 1024))
    else -> String.format(java.util.Locale.getDefault(), "%d KB", bytes / 1024)
}
