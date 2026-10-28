package com.sonora.music.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonora.music.Lyrics
import com.sonora.music.PlayerViewModel

/** Pannello dei testi: sostituisce la copertina e segue il brano riga per riga. */
@Composable
fun LyricsPanel(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val lyrics = vm.lyrics
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = androidx.compose.ui.graphics.Color.White
    ) {
        when {
            vm.lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BouncyLoader()
            }

            lyrics == null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Testo non disponibile per questo brano",
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }

            else -> LyricsList(vm, lyrics)
        }
    }
}

@Composable
private fun LyricsList(vm: PlayerViewModel, lyrics: Lyrics) {
    val listState = rememberLazyListState()
    val active by remember(lyrics) { derivedStateOf { lyrics.activeIndex(vm.positionMs) } }

    // La riga attuale viene portata verso il centro con uno scorrimento morbido.
    LaunchedEffect(active, lyrics) {
        if (lyrics.synced && active >= 0) {
            val offset = -(listState.layoutInfo.viewportSize.height / 3)
            listState.animateScrollToItem(active, offset)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricLineItem(
                text = line.text,
                active = !lyrics.synced || index == active,
                distance = if (lyrics.synced) kotlin.math.abs(index - active) else 0,
                onClick = if (lyrics.synced) ({ vm.seekToMs(line.timeMs) }) else null
            )
        }
    }
}

@Composable
private fun LyricLineItem(text: String, active: Boolean, distance: Int, onClick: (() -> Unit)?) {
    val lineAlpha by animateFloatAsState(
        targetValue = if (active) 1f else (0.5f - 0.06f * distance).coerceAtLeast(0.2f),
        animationSpec = tween(300),
        label = "lineAlpha"
    )
    val lineScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.86f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "lineScale"
    )

    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = lineAlpha
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            // Solo la riga attuale è nitida: sopra e sotto il testo si sfoca sempre di più.
            .then(if (!active && distance > 0) Modifier.blur((distance * 1.6f).coerceAtMost(7f).dp) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}
