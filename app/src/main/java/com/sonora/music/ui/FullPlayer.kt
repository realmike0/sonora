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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
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
    val back = predictiveBack(enabled = !closing) { if (showLyrics) showLyrics = false else closing = true }

    val white = Color.White
    val soft = Color.White.copy(alpha = 0.72f)
    val density = LocalDensity.current

    CompositionLocalProvider(LocalContentColor provides white) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .backEffect(back)
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
                        onVerticalDrag = { _, dy -> if (!showLyrics) drag = (drag + dy).coerceAtLeast(0f) }
                    )
                }
        ) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val endH = minOf(screenW, screenH * 0.48f)
            val start = miniCoverRect
            val startX = start?.left ?: with(density) { 28.dp.toPx() }
            val startY = start?.top ?: (screenH - with(density) { 170.dp.toPx() })
            val startW = start?.width ?: with(density) { 52.dp.toPx() }
            val startH = start?.height ?: with(density) { 52.dp.toPx() }
            val startCorner = with(density) { 16.dp.toPx() }
            val lyricsA by animateFloatAsState(if (showLyrics) 1f else 0f, tween(320), label = "lyricsA")

            // Sfondo: copertina sfocata e scurita (più scura con i testi).
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            ) {
                Artwork(np.artwork, Modifier.fillMaxSize().softBlur(60.dp), RectangleShape, px = 160)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f + 0.17f * lyricsA)))
            }

            // La copertina che cresce (sfuma via quando si aprono i testi).
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
                        alpha = 1f - lyricsA
                        scaleX = 1f + 0.06f * lyricsA
                        scaleY = 1f + 0.06f * lyricsA
                    }
            ) {
                Artwork(np.artwork, Modifier.fillMaxSize(), RectangleShape, px = 1000)
                if (vm.videoOn) {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        VideoSurface(vm, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    }
                }
                // La copertina si fonde con lo sfondo sotto.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)))
                )
            }

            // Modalità player
            if (lyricsA < 0.99f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - lyricsA }) {
                    // Barra in alto: ⌄ a sinistra; video, coda e menu a destra.
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
                        Spacer(Modifier.weight(1f))
                        MorphIconButton(
                            onClick = { vm.setVideo(!vm.videoOn) }, size = 44.dp,
                            container = if (vm.videoOn) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.35f)
                        ) {
                            Icon(Icons.Rounded.Videocam, "Video", tint = if (vm.videoOn) Color.Black else Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        MorphIconButton(onClick = { vm.queueOpen = true }, size = 44.dp, container = Color.Black.copy(alpha = 0.35f)) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = "Coda")
                        }
                        Spacer(Modifier.width(8.dp))
                        MorphIconButton(onClick = { vm.menuTrack = np.toTrack() }, size = 44.dp, container = Color.Black.copy(alpha = 0.35f)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Altro")
                        }
                    }

                    val coverBottom = with(density) { endH.toDp() }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = coverBottom - 28.dp)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .graphicsLayer {
                                val t = ((progress.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                alpha = t
                                translationY = (1f - t) * 48.dp.toPx()
                            },
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    np.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = white, maxLines = 1, overflow = TextOverflow.Ellipsis
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
                                        np.artist, style = MaterialTheme.typography.titleMedium, color = soft,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(Icons.Rounded.ChevronRight, null, tint = soft, modifier = Modifier.size(20.dp))
                                }
                            }
                            LikeButton(liked = vm.store.isLiked(np.id), onClick = { vm.store.toggleLike(np.toTrack()) }, tint = white)
                        }
                        WavySeekBar(vm)
                        TransportRow(vm)
                        PlayerBottomRow(vm, onLyrics = { showLyrics = true })
                    }
                }
            }

            // Modalità testi
            if (lyricsA > 0.01f) {
                LyricsScreen(
                    vm = vm,
                    title = np.title,
                    artist = np.artist,
                    onClose = { closing = true },
                    onBackToPlayer = { showLyrics = false },
                    modifier = Modifier.graphicsLayer { alpha = lyricsA }
                )
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
internal fun PlayButton(vm: PlayerViewModel, size: Dp = 84.dp) {
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
            .size(size)
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
                modifier = Modifier.size(size * 0.52f)
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

/** Superficie dove viene mostrato il video originale. */
@Composable
fun VideoSurface(vm: PlayerViewModel, modifier: Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx -> android.view.TextureView(ctx).also { vm.bindVideo(it) } },
        modifier = modifier,
        onRelease = { vm.bindVideo(null) }
    )
}
