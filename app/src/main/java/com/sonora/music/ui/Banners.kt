package com.sonora.music.ui

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.Headsets
import com.sonora.music.PlayerViewModel
import com.sonora.music.Updater

private val BANNER_SPRING = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)

/** Avviso a tendina in alto: aggiornamento disponibile. (Cuffie e speaker hanno la loro schermata: DeviceSheet.) */
@Composable
fun BannerHost(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val update = vm.update
    val showUpdate = update != null && !vm.updateDismissed

    Column(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(
            visible = showUpdate,
            enter = slideInVertically(BANNER_SPRING) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            if (update != null) UpdateCard(vm, update)
        }
    }
}

@Composable
private fun BannerSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun UpdateCard(vm: PlayerViewModel, rel: Updater.Release) {
    BannerSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge { Icon(Icons.Rounded.Download, contentDescription = null) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Aggiornamento disponibile",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Sonora ${rel.tag.removePrefix("v")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val progress = vm.updateProgress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            vm.updateMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.downloadUpdate() }, enabled = progress == null) {
                    Text(if (progress == null) "Scarica e installa" else "Scarico… ${(progress * 100).toInt()}%")
                }
                TextButton(onClick = { vm.updateDismissed = true }) { Text("Dopo") }
            }
        }
    }
}

@Composable
private fun IconBadge(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer
        ) { content() }
    }
}

/** Tiene aggiornato vm.headset: dispositivo audio già collegato all'apertura o collegato mentre l'app è aperta. */
@Composable
fun HeadsetWatcher(vm: PlayerViewModel) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vm.headset = Headsets.scan(context, am)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val dev = addedDevices.firstOrNull { Headsets.isHeadsetDevice(it) } ?: return
                val info = Headsets.classify(context, dev)
                if (info != vm.headset) vm.headsetDismissed = false
                vm.headset = info
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                vm.headset = Headsets.scan(context, am)
            }
        }
        am.registerAudioDeviceCallback(callback, null)
        onDispose { am.unregisterAudioDeviceCallback(callback) }
    }
}
