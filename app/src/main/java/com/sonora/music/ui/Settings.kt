package com.sonora.music.ui

import androidx.compose.foundation.clickable
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

@Composable
fun SettingsScreen(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val store = vm.store

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Impostazioni",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(top = 16.dp)
            )

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
        }

        Watermark(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 12.dp)
        )
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
                        store.setLyricsSource(source)
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
