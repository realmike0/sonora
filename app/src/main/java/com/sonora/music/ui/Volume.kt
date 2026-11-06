package com.sonora.music.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.music.DeviceKind
import com.sonora.music.Headsets
import kotlinx.coroutines.delay

private data class Output(val kind: DeviceKind?, val name: String, val icon: ImageVector)

/** Dispositivo che sta riproducendo: cuffie, auricolari, speaker o auto (riconosciuti dall'algoritmo di Headsets) oppure il telefono. */
private fun currentOutput(c: Context, am: AudioManager): Output {
    val info = Headsets.scan(c, am)
    if (info != null) {
        val icon = when (info.kind) {
            DeviceKind.SPEAKER -> Icons.Rounded.Speaker
            DeviceKind.CAR -> Icons.Rounded.DirectionsCar
            else -> Icons.Rounded.Headphones
        }
        return Output(info.kind, info.name, icon)
    }
    val speaker = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    return Output(null, if (speaker) "Altoparlante del telefono" else "Telefono", Icons.Rounded.PhoneAndroid)
}

private fun kindName(k: DeviceKind?) = when (k) {
    DeviceKind.EARBUDS -> "Auricolari"
    DeviceKind.HEADPHONES -> "Cuffie"
    DeviceKind.SPEAKER -> "Speaker"
    DeviceKind.CAR -> "Auto"
    null -> "Telefono"
}

/** Pillola del volume (Material 3): mostra dispositivo e livello; al tocco si apre il menu a tendina con il regolatore. */
@Composable
fun VolumePill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var out by remember { mutableStateOf(currentOutput(context, am)) }
    var open by remember { mutableStateOf(false) }

    // Segue anche i tasti fisici del volume e i cambi di dispositivo.
    LaunchedEffect(Unit) {
        while (true) {
            vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            out = currentOutput(context, am)
            delay(700)
        }
    }

    Box(modifier) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(out.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Icon(volumeIcon(vol, max), contentDescription = tr("Volume"), tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("${vol * 100 / max}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.widthIn(min = 280.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(out.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(kindName(out.kind), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            out.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                PillSlider(
                    value = vol,
                    max = max,
                    onChange = {
                        vol = it
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
                    }
                )
                Spacer(Modifier.height(4.dp))
                // Cambia dispositivo di uscita: cuffie e altoparlanti Bluetooth, Chromecast e altri trovati dal sistema.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(onClick = {
                    open = false
                    openOutputSwitcher(ctx)
                }) { Text(tr("Altri dispositivi…")) }
            }
        }
    }
}

private fun volumeIcon(v: Int, max: Int): ImageVector = when {
    v == 0 -> Icons.Rounded.VolumeOff
    v * 2 < max -> Icons.Rounded.VolumeDown
    else -> Icons.Rounded.VolumeUp
}

/** Regolatore a pillola grande: si trascina o si tocca; ogni scatto dà una vibrazione leggerissima. */
@Composable
private fun PillSlider(value: Int, max: Int, onChange: (Int) -> Unit) {
    val view = LocalView.current
    val fraction by animateFloatAsState(
        targetValue = value / max.toFloat(),
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "volFill"
    )
    var widthPx by remember { mutableIntStateOf(1) }
    var last by remember { mutableIntStateOf(value) }

    fun setFrom(x: Float) {
        val v = ((x / widthPx) * max).let { Math.round(it) }.coerceIn(0, max)
        if (v != last) {
            last = v
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange(v)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(max) {
                detectTapGestures { setFrom(it.x) }
            }
            .pointerInput(max) {
                detectHorizontalDragGestures { change, _ -> setFrom(change.position.x) }
            }
            .androidx_onSize { widthPx = it }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0.08f, 1f))
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(Modifier.fillMaxHeight().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                volumeIcon(value, max),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun Modifier.androidx_onSize(f: (Int) -> Unit): Modifier =
    this.onSizeChanged { f(it.width) }

/** Apre il selettore di uscita del sistema (Bluetooth, Cast...). Se il telefono non lo ha, apre le impostazioni Bluetooth. */
fun openOutputSwitcher(c: android.content.Context) {
    val attempts = listOf(
        android.content.Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", c.packageName),
        android.content.Intent(android.provider.Settings.Panel.ACTION_VOLUME),
        android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
    )
    for (i in attempts) {
        try {
            c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
        }
    }
}
