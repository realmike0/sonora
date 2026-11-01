package com.sonora.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SoftWhite = Color.White.copy(alpha = 0.16f)

/** Barra di avanzamento "a onda": la parte già suonata ondeggia (solo mentre suona), il resto è una linea. */
@Composable
internal fun WavySeekBar(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(vm.wantsPlay) {
        if (vm.wantsPlay) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { t ->
                    if (last != 0L) phase += (t - last) / 1_000_000_000f * 2.2f
                    last = t
                }
            }
        }
    }
    // L'ampiezza dell'onda sparisce quando la musica è in pausa.
    val amp by animateFloatAsState(if (vm.wantsPlay) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "waveAmp")
    val frac = (dragging ?: vm.progress).coerceIn(0f, 1f)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o -> vm.seekToFraction((o.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let(vm::seekToFraction)
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            val w = size.width
            val cy = size.height / 2f
            val x = (w * frac).coerceIn(0f, w)
            val stroke = 4.dp.toPx()
            val a = 4.dp.toPx() * amp
            val wl = 26.dp.toPx()
            // parte restante
            drawLine(Color.White.copy(alpha = 0.28f), Offset(x, cy), Offset(w, cy), stroke, StrokeCap.Round)
            // parte suonata (onda)
            val path = Path()
            var px = 0f
            path.moveTo(0f, cy)
            while (px <= x) {
                val env = (px / (12.dp.toPx())).coerceIn(0f, 1f) // parte piatta all'inizio
                path.lineTo(px, cy + a * env * sin((px / wl) * 2f * PI.toFloat() - phase).toFloat())
                px += 2f
            }
            path.lineTo(x, cy + a * sin((x / wl) * 2f * PI.toFloat() - phase).toFloat())
            drawPath(path, Color.White, style = Stroke(stroke, cap = StrokeCap.Round))
            // cursore
            drawRoundRect(
                Color.White,
                topLeft = Offset(x - 2.dp.toPx(), cy - 11.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 22.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = (frac * vm.durationMs).toLong()
            val left = (vm.durationMs - shown).coerceAtLeast(0L)
            val c = Color.White.copy(alpha = 0.75f)
            Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = c)
            Text("-" + formatTime(left), style = MaterialTheme.typography.labelMedium, color = c)
        }
    }
}

/** Pulsante tondo traslucido (precedente, successivo, testi, casuale...). */
@Composable
internal fun RoundAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "raScale"
    )
    val corner by animateIntAsState(
        if (pressed) 32 else 50, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "raCorner"
    )
    FilledIconButton(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(percent = corner.coerceIn(0, 50)),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (selected) Color.White else SoftWhite,
            contentColor = if (selected) Color.Black else Color.White
        ),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        content = content
    )
}

/** Pulsante pausa/play a pillola larga: diventa più "quadrato" quando suona. */
@Composable
internal fun PausePill(vm: PlayerViewModel, modifier: Modifier = Modifier, width: Dp = 132.dp, height: Dp = 72.dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "ppScale"
    )
    val corner by animateIntAsState(
        if (vm.wantsPlay) 32 else 50, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "ppCorner"
    )
    FilledIconButton(
        onClick = vm::togglePlay,
        interactionSource = source,
        shape = RoundedCornerShape(percent = corner.coerceIn(0, 50)),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AnimatedContent(
            targetState = vm.wantsPlay,
            transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "ppIcon"
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pausa" else "Riproduci",
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

/** Riga dei controlli: precedente tondo | pausa a pillola | successivo tondo. */
@Composable
internal fun TransportRow(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundAction(onClick = vm::previous, size = 72.dp) {
            Icon(Icons.Rounded.SkipPrevious, "Precedente", Modifier.size(34.dp))
        }
        Box(Modifier.width(10.dp))
        PausePill(vm)
        Box(Modifier.width(10.dp))
        RoundAction(onClick = vm::next, size = 72.dp) {
            Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(34.dp))
        }
    }
}

/** Riga in basso del player: testi | volume | casuale. */
@Composable
internal fun PlayerBottomRow(vm: PlayerViewModel, onLyrics: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundAction(onClick = onLyrics, size = 56.dp) { Icon(Icons.Rounded.Lyrics, "Testi") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { VolumePill() }
        RoundAction(onClick = vm::toggleShuffle, size = 56.dp, selected = vm.shuffleOn) {
            Icon(Icons.Rounded.Shuffle, "Riproduzione casuale")
        }
    }
}

/**
 * Schermata dei testi: titolo al centro, testo grande, e in basso tre tasti. Il tasto centrale "^"
 * si trasforma (cerchio → pillola alta → ... → riga di controlli) e sale sopra gli altri due.
 */
@Composable
internal fun LyricsScreen(
    vm: PlayerViewModel,
    title: String,
    artist: String,
    onClose: () -> Unit,
    onBackToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val p = remember { Animatable(0f) }
    fun toggle(open: Boolean) {
        scope.launch { p.animateTo(if (open) 1f else 0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
    }
    Box(modifier.fillMaxSize()) {
        // Testo
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 76.dp, bottom = 96.dp)
                .pointerInput(Unit) { detectTapGestures { if (p.value > 0.5f) toggle(false) } }
        ) {
            LyricsPanel(vm, Modifier.fillMaxSize())
        }
        // Intestazione
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            RoundAction(onClick = onClose, size = 44.dp, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Chiudi", Modifier.size(28.dp))
            }
            RoundAction(
                onClick = vm::toggleTranslate, size = 44.dp, selected = vm.translateOn,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { Icon(Icons.Rounded.Translate, tr("Traduci testo")) }
            Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Text(
                    artist, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }
        // Parte bassa
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(168.dp)
        ) {
            val t = p.value
            RoundAction(onClick = onBackToPlayer, size = 60.dp, modifier = Modifier.align(Alignment.BottomStart)) {
                Icon(Icons.Rounded.Album, "Torna al player")
            }
            RoundAction(
                onClick = vm::toggleShuffle, size = 60.dp, selected = vm.shuffleOn,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) { Icon(Icons.Rounded.Shuffle, "Riproduzione casuale") }

            // Cluster che si trasforma. Larghezza 60 → piena, altezza con "gobba" a metà animazione.
            val full = maxWidth
            val w = 60.dp + (full - 60.dp) * t.coerceIn(0f, 1f)
            val bump = (sin(t.coerceIn(0f, 1f) * PI.toFloat()) * 26f).dp
            val h = 60.dp + 12.dp * t.coerceIn(0f, 1f) + bump
            val rise = (88.dp * t.coerceIn(0f, 1f))
            val corner = (30f * (1f - 0.35f * t.coerceIn(0f, 1f))).coerceAtLeast(18f)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -rise)
                    .size(w, h)
                    .clip(RoundedCornerShape(corner.dp))
                    .background(if (t < 0.5f) SoftWhite else Color.Transparent)
                    .pointerInput(Unit) { detectTapGestures { if (p.value < 0.5f) toggle(true) } },
                contentAlignment = Alignment.Center
            ) {
                // freccia: sparisce mentre si apre
                Icon(
                    Icons.Rounded.KeyboardArrowUp, "Controlli",
                    Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            alpha = (1f - t * 2.2f).coerceIn(0f, 1f)
                            rotationZ = 180f * t
                        },
                    tint = Color.White
                )
                // controlli: appaiono quando c'è spazio
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f) },
                    contentAlignment = Alignment.Center
                ) {
                    if (t > 0.5f) TransportRow(vm)
                }
            }
        }
    }
}
