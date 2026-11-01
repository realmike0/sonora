package com.sonora.music.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException

/** Avanzamento del gesto "indietro" (0..1) per l'anteprima predittiva. */
class BackProgress {
    var value by mutableFloatStateOf(0f)
}

/** Gesto indietro predittivo (Android 14+): mentre trascini la schermata si rimpicciolisce; se rilasci il gesto torna com'era. */
@Composable
fun predictiveBack(enabled: Boolean = true, onBack: () -> Unit): BackProgress {
    val p = remember { BackProgress() }
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { p.value = it.progress }
            onBack()
            p.value = 0f
        } catch (e: CancellationException) {
            p.value = 0f
        }
    }
    return p
}

/** Effetto dell'anteprima: la schermata si riduce, si sposta e si arrotonda. */
fun Modifier.backEffect(p: BackProgress): Modifier = this
    .graphicsLayer {
        val v = p.value
        val s = 1f - 0.12f * v
        scaleX = s
        scaleY = s
        translationX = size.width * 0.05f * v
        alpha = 1f - 0.25f * v
    }
    .clip(RoundedCornerShape(0.dp))
