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
    Text(tr("Formato"), style = MaterialTheme.typography.labelMedium)
    Chips(codecs.map { it.second }, codecs.indexOfFirst { it.first == codec }) {
        codec = codecs[it].first
        AudioPrefs.save(c, codec, level)
    }
    Text(tr("Qualità"), style = MaterialTheme.typography.labelMedium)
    Chips(levels.map { it.second }, levels.indexOfFirst { it.first == level }) {
        level = levels[it].first
        AudioPrefs.save(c, codec, level)
    }
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
        Text(tr("Dissolvenza tra i brani"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = { on = it; p.edit().putBoolean("mix_fade", it).apply() })
    }
    if (on) {
        var overlap by remember { mutableStateOf(p.getBoolean("mix_overlap", true)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Sovrapposizione vera"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("I due brani suonano insieme (con la grafica leggera si usa la dissolvenza semplice)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = overlap, onCheckedChange = { overlap = it; p.edit().putBoolean("mix_overlap", it).apply() })
        }
        Text("Uscita: %.1f s".format(out), style = MaterialTheme.typography.labelMedium)
        Slider(value = out, onValueChange = { out = it }, valueRange = 0f..12f,
            onValueChangeFinished = { p.edit().putInt("fade_out_ms", (out * 1000).toInt()).apply() })
        Text("Ingresso: %.1f s".format(inn), style = MaterialTheme.typography.labelMedium)
        Slider(value = inn, onValueChange = { inn = it }, valueRange = 0f..12f,
            onValueChangeFinished = { p.edit().putInt("fade_in_ms", (inn * 1000).toInt()).apply() })
        Text(tr("Curva"), style = MaterialTheme.typography.labelMedium)
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
}

@Composable
fun HapticsSection() {
    val c = LocalContext.current
    val p = remember { prefs(c) }
    var on by remember { mutableStateOf(p.getBoolean("haptics", true)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Feedback aptico al tocco"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Una vibrazione leggerissima quando tocchi qualcosa."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = on, onCheckedChange = { on = it; p.edit().putBoolean("haptics", it).apply() })
    }
}

/** Haptics audio: il telefono vibra a tempo con i bassi. Il permesso si chiede quando si accende. */
@Composable
fun AudioHapticsSection() {
    val c = LocalContext.current
    var on by remember { mutableStateOf(com.sonora.music.AudioHaptics.enabled(c)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        on = ok
        com.sonora.music.AudioHaptics.setEnabled(c, ok)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Haptics audio"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = { want ->
            if (!want) {
                on = false
                com.sonora.music.AudioHaptics.setEnabled(c, false)
            } else if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                on = true
                com.sonora.music.AudioHaptics.setEnabled(c, true)
            } else {
                ask.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        })
    }
    if (on) {
        var sens by remember { mutableFloatStateOf(com.sonora.music.AudioHaptics.sensitivity(c)) }
        androidx.compose.material3.Slider(
            value = sens,
            onValueChange = { sens = it; com.sonora.music.AudioHaptics.setSensitivity(c, it) }
        )
    }
}

@Composable
fun EqualizerSection() {
    val c = LocalContext.current
    val info = remember { com.sonora.music.Eq.info() }
    if (info == null) {
        Text(tr("Avvia un brano per regolare l'equalizzatore (o il telefono non lo supporta)."), style = MaterialTheme.typography.bodySmall)
        return
    }
    var on by remember { mutableStateOf(com.sonora.music.Eq.enabled(c)) }
    var levels by remember { mutableStateOf(com.sonora.music.Eq.levels(c).let { l -> List(info.bands) { l.getOrNull(it) ?: 0 } }) }
    var bass by remember { mutableFloatStateOf(com.sonora.music.Eq.bass(c).toFloat()) }
    var loud by remember { mutableFloatStateOf(com.sonora.music.Eq.loud(c).toFloat()) }
    fun save() = com.sonora.music.Eq.save(c, on, levels, bass.toInt(), loud.toInt())

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Equalizzatore"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = { on = it; save() })
    }
    if (on) {
        Chips(info.presets, -1) { i ->
            com.sonora.music.Eq.presetLevels(i)?.let { levels = it; save() }
        }
        info.freqsHz.forEachIndexed { i, hz ->
            Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = levels[i].toFloat(),
                onValueChange = { v -> levels = levels.toMutableList().also { it[i] = v.toInt() } },
                valueRange = info.minMb.toFloat()..info.maxMb.toFloat(),
                onValueChangeFinished = { save() }
            )
        }
        Text(tr("Bassi"), style = MaterialTheme.typography.labelMedium)
        Slider(value = bass, onValueChange = { bass = it }, valueRange = 0f..1000f, onValueChangeFinished = { save() })
        Text(tr("Volume extra"), style = MaterialTheme.typography.labelMedium)
        Slider(value = loud, onValueChange = { loud = it }, valueRange = 0f..1000f, onValueChangeFinished = { save() })
    }
}
