package com.sonora.music.ui

import androidx.compose.foundation.layout.Column
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
    Surface(
        shape = RoundedCornerShape(AppStyle.card),
        color = accent.copy(alpha = 0.16f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("AI", style = MaterialTheme.typography.labelLarge, color = accent)
                Text(
                    rating?.score?.let { "%.1f".format(it) } ?: "…",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                rating?.verdict ?: tr("L'AI sta ascoltando…"),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
