package com.sonora.music.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonora.music.AudioPrefs
import com.sonora.music.Perf
import com.sonora.music.PerfMode

private fun prefs(c: Context) = c.getSharedPreferences("sonora", Context.MODE_PRIVATE)

@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun AudioQualitySection() {
    val c = LocalContext.current
    var codec by remember { mutableStateOf(AudioPrefs.codec) }
    var level by remember { mutableStateOf(AudioPrefs.level) }
    val codecs = listOf("m4a" to "M4A (AAC)", "webm" to "WebM (Opus)")
    val levels = listOf("high" to "Alta", "medium" to "Media", "low" to "Bassa (risparmio dati)")
    Text("Formato", style = MaterialTheme.typography.labelMedium)
    Chips(codecs.map { it.second }, codecs.indexOfFirst { it.first == codec }) {
        codec = codecs[it].first
        AudioPrefs.save(c, codec, level)
    }
    Text("Qualità", style = MaterialTheme.typography.labelMedium)
    Chips(levels.map { it.second }, levels.indexOfFirst { it.first == level }) {
        level = levels[it].first
        AudioPrefs.save(c, codec, level)
    }
    Text(
        "FLAC e MP3 non sono disponibili: YouTube offre solo AAC e Opus. I brani già in cache mantengono la qualità con cui sono stati scaricati.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun FadeSection() {
    val c = LocalContext.current
    val p = remember { prefs(c) }
    var on by remember { mutableStateOf(p.getBoolean("mix_fade", true)) }
    var out by remember { mutableFloatStateOf(p.getInt("fade_out_ms", 4000) / 1000f) }
    var inn by remember { mutableFloatStateOf(p.getInt("fade_in_ms", 2500) / 1000f) }
    var curve by remember { mutableStateOf(p.getInt("fade_curve", 0)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Dissolvenza tra i brani", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = { on = it; p.edit().putBoolean("mix_fade", it).apply() })
    }
    if (on) {
        Text("Uscita: %.1f s".format(out), style = MaterialTheme.typography.labelMedium)
        Slider(value = out, onValueChange = { out = it }, valueRange = 0f..12f,
            onValueChangeFinished = { p.edit().putInt("fade_out_ms", (out * 1000).toInt()).apply() })
        Text("Ingresso: %.1f s".format(inn), style = MaterialTheme.typography.labelMedium)
        Slider(value = inn, onValueChange = { inn = it }, valueRange = 0f..12f,
            onValueChangeFinished = { p.edit().putInt("fade_in_ms", (inn * 1000).toInt()).apply() })
        Text("Curva", style = MaterialTheme.typography.labelMedium)
        Chips(listOf("Morbida", "Lineare", "Radiofonica"), curve) {
            curve = it
            p.edit().putInt("fade_curve", it).apply()
        }
    }
}

@Composable
fun PerfSection() {
    val c = LocalContext.current
    val modes = PerfMode.values()
    Chips(modes.map { it.label }, Perf.mode.value.ordinal) { Perf.setMode(c, modes[it]) }
    Text(
        (if (Perf.lite.value) "Grafica leggera attiva (sfocature e animazioni infinite spente). " else "Grafica completa. ") + Perf.summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
fun HapticsSection() {
    val c = LocalContext.current
    val p = remember { prefs(c) }
    var on by remember { mutableStateOf(p.getBoolean("haptics", true)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Feedback aptico al tocco", style = MaterialTheme.typography.titleMedium)
            Text(
                "Una vibrazione leggerissima quando tocchi qualcosa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = on, onCheckedChange = { on = it; p.edit().putBoolean("haptics", it).apply() })
    }
}
