@file:OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sonora.music.ArtistRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track
import com.sonora.music.toTrack
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Player a schermo intero (stile "Apple Music"): la copertina parte dal mini player e cresce
 * fino a occupare tutta la larghezza in alto, mentre sotto sfuma la copertina sfocata e
 * compaiono titolo, barra e controlli. Tutto il testo è bianco, sempre leggibile.
 * Si chiude con la freccia, con "indietro" o trascinando verso il basso.
 */
@Composable
fun FullPlayer(vm: PlayerViewModel, onDismiss: () -> Unit, onArtist: (String) -> Unit) {
    val np = vm.nowPlaying ?: return
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var pendingArtist by remember { mutableStateOf<String?>(null) }
    var pickArtist by remember { mutableStateOf<List<String>?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    var addTrack by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onDismiss()
            pendingArtist?.let(onArtist)
        }
    }
    BackHandler(enabled = !closing) { closing = true }

    val white = Color.White
    val soft = Color.White.copy(alpha = 0.72f)
    val density = LocalDensity.current

    CompositionLocalProvider(LocalContentColor provides white) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = drag }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag > 260f) {
                                closing = true
                            } else {
                                scope.launch {
                                    animate(drag, 0f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) { v, _ ->
                                        drag = v
                                    }
                                }
                            }
                        },
                        onDragCancel = { drag = 0f },
                        onVerticalDrag = { _, dy -> drag = (drag + dy).coerceAtLeast(0f) }
                    )
                }
        ) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            // Copertina grande: tutta la larghezza, alta come la larghezza (ma non oltre il 48% dello schermo).
            val endH = minOf(screenW, screenH * 0.48f)
            val start = miniCoverRect
            val startX = start?.left ?: with(density) { 28.dp.toPx() }
            val startY = start?.top ?: (screenH - with(density) { 170.dp.toPx() })
            val startW = start?.width ?: with(density) { 52.dp.toPx() }
            val startH = start?.height ?: with(density) { 52.dp.toPx() }
            val startCorner = with(density) { 16.dp.toPx() }

            // Sfondo: copertina sfocata e scurita (la sfocatura vera c'è da Android 12; prima resta solo il colore).
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            ) {
                Artwork(np.artwork, Modifier.fillMaxSize().blur(60.dp), RectangleShape, px = 160)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
            }

            // La copertina che cresce.
            Box(
                Modifier
                    .offset {
                        val p = progress.value.coerceIn(0f, 1f)
                        IntOffset(lerp(startX, 0f, p).roundToInt(), lerp(startY, 0f, p).roundToInt())
                    }
                    .layout { measurable, _ ->
                        val p = progress.value.coerceIn(0f, 1f)
                        val w = lerp(startW, screenW, p).roundToInt().coerceAtLeast(1)
                        val h = lerp(startH, endH, p).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        shape = RoundedCornerShape(CornerSize(lerp(startCorner, 0f, progress.value.coerceIn(0f, 1f))))
                        clip = true
                    }
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "artOrLyrics"
                ) { lyricsMode ->
                    Box(Modifier.fillMaxSize()) {
                        Artwork(np.artwork, Modifier.fillMaxSize(), RectangleShape, px = 1000)
                        if (lyricsMode) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
                            LyricsPanel(vm, Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp))
                        } else {
                            // Sfumatura in basso: la copertina si fonde con lo sfondo.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.55f to Color.Transparent,
                                            1f to Color.Black.copy(alpha = 0.55f)
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // Barra in alto (sopra la copertina).
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = ((progress.value - 0.4f) / 0.6f).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorphIconButton(onClick = { closing = true }, size = 44.dp, container = Color.Black.copy(alpha = 0.35f)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Chiudi", Modifier.size(28.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                }
                MorphIconButton(onClick = { vm.menuTrack = np.toTrack() }, size = 44.dp, container = Color.Black.copy(alpha = 0.35f)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Altro")
                }
            }

            // Tutto il resto, sotto la copertina: compare quando è quasi arrivata.
            val coverBottom = with(density) { endH.toDp() }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = coverBottom)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val t = ((progress.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * 48.dp.toPx()
                    },
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Titolo, artista (toccabile) e preferito
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            np.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = white,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val names = ArtistRepository.splitArtists(np.artist)
                                    when {
                                        names.size > 1 -> pickArtist = names
                                        names.size == 1 -> {
                                            pendingArtist = names[0]
                                            closing = true
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                np.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = soft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = soft, modifier = Modifier.size(20.dp))
                        }
                    }
                    LikeButton(liked = vm.store.isLiked(np.id), onClick = { vm.store.toggleLike(np.toTrack()) }, tint = white)
                }

                Column { SeekBar(vm, tint = white) }

                // Controlli principali
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MorphIconButton(onClick = vm::previous, size = 72.dp) {
                        Icon(Icons.Rounded.SkipPrevious, "Precedente", Modifier.size(44.dp))
                    }
                    PlayButton(vm)
                    MorphIconButton(onClick = vm::next, size = 72.dp) {
                        Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(44.dp))
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showLyrics,
                        onClick = { showLyrics = !showLyrics },
                        label = { Text("Testi") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            labelColor = white,
                            selectedContainerColor = white,
                            selectedLabelColor = Color.Black
                        ),
                        border = null
                    )
                    Spacer(Modifier.width(12.dp))
                    MorphIconButton(onClick = { addTrack = np.toTrack() }, size = 48.dp, container = Color.White.copy(alpha = 0.14f)) {
                        Icon(Icons.Rounded.Add, contentDescription = "Aggiungi a playlist")
                    }
                }
            }
        }
    }

    pickArtist?.let { names ->
        AlertDialog(
            onDismissRequest = { pickArtist = null },
            title = { Text("Scegli l'artista") },
            text = {
                Column {
                    names.forEach { n ->
                        TextButton(onClick = {
                            pickArtist = null
                            pendingArtist = n
                            closing = true
                        }) { Text(n, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickArtist = null }) { Text("Annulla") } }
        )
    }

    addTrack?.let { AddToPlaylistDialog(vm.store, it) { addTrack = null } }
}

/** Play/pausa grande: cerchio bianco che diventa quadrato smussato quando suona, con icona che "scoppia". */
@Composable
private fun PlayButton(vm: PlayerViewModel) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "playScale"
    )
    val corner by animateIntAsState(
        targetValue = if (vm.wantsPlay) 30 else 50,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "playCorner"
    )
    FilledIconButton(
        onClick = vm::togglePlay,
        interactionSource = source,
        shape = RoundedCornerShape(percent = corner.coerceIn(0, 50)),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = Modifier
            .size(84.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AnimatedContent(
            targetState = vm.wantsPlay,
            transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "playIcon"
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pausa" else "Riproduci",
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

/**
 * Pulsante Material 3 con animazione di forma: quando lo premi si schiaccia e passa da cerchio
 * a quadrato arrotondato, poi torna indietro con effetto molla.
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    container: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "morphScale"
    )
    val corner by animateIntAsState(
        targetValue = if (pressed) 28 else 50,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "morphCorner"
    )
    FilledIconButton(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(percent = corner.coerceIn(0, 50)),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (pressed && container == Color.Transparent) Color.White.copy(alpha = 0.16f) else container,
            contentColor = Color.White
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
