package com.sonora.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.music.Ai

/** Voto dell'AI a un album o a un artista, con un giudizio in poche parole. */
@Composable
fun AiRatingCard(kind: String, name: String, artist: String, tracks: List<String>, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var rating by remember(kind, name, artist) { mutableStateOf<Ai.Rating?>(null) }
    var failed by remember(kind, name, artist) { mutableStateOf(false) }
    LaunchedEffect(kind, name, artist, Lang.code) {
        failed = false
        rating = try {
            Ai.rate(context, kind, name, artist, tracks)
        } catch (e: Exception) {
            failed = true
            null
        }
    }
    if (failed) return
    val r = rating
    Surface(
        shape = RoundedCornerShape(AppStyle.card),
        color = accent.copy(alpha = 0.16f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiDonut(r, accent)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("AI", style = MaterialTheme.typography.labelLarge, color = accent)
                    Text(r?.verdict ?: tr("L'AI sta ascoltando…"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (r != null && r.meaning > 0) {
                Spacer(Modifier.height(12.dp))
                val cols = AiColors(accent)
                listOf(tr("Significato") to r.meaning, tr("Melodia") to r.melody, "Flow" to r.flow).forEachIndexed { i, (label, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(10.dp).background(cols[i], CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("%.1f".format(v), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            if (r != null && r.tracks.any { it > 0 }) {
                Spacer(Modifier.height(12.dp))
                Text(tr("Voti dei brani"), style = MaterialTheme.typography.labelLarge, color = accent)
                tracks.take(r.tracks.size).forEachIndexed { i, title ->
                    val v = r.tracks[i]
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (v > 0) "%.1f".format(v) else "–", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

private fun AiColors(accent: Color) = listOf(accent, accent.copy(alpha = 0.6f), accent.copy(alpha = 0.3f))

/** Grafico a torta ad anello: tre spicchi (significato, melodia, flow) e il voto totale al centro. */
@Composable
private fun AiDonut(r: Ai.Rating?, accent: Color) {
    val cols = AiColors(accent)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val d = Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, tl, d, style = Stroke(stroke))
            if (r != null && r.meaning > 0) {
                val parts = listOf(r.meaning, r.melody, r.flow)
                val sum = parts.sum().toFloat().coerceAtLeast(0.1f)
                var start = -90f
                parts.forEachIndexed { i, v ->
                    val sweep = 360f * (v.toFloat() / sum)
                    drawArc(cols[i], start + 2f, (sweep - 4f).coerceAtLeast(1f), false, tl, d, style = Stroke(stroke))
                    start += sweep
                }
            } else if (r != null) {
                drawArc(accent, -90f, 360f * (r.score.toFloat() / 10f), false, tl, d, style = Stroke(stroke))
            }
        }
        Text(
            r?.score?.let { "%.1f".format(it) } ?: "…",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
        )
    }
}
