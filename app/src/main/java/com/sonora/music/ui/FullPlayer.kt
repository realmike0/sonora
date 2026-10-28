@file:OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track
import com.sonora.music.toTrack
import kotlinx.coroutines.launch

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Player a schermo intero. All'apertura la copertina "cresce" dal mini player fino a occupare
 * il suo posto grande; il resto dei controlli sale e compare subito dopo.
 * Si chiude con la freccia, con il tasto indietro o trascinando verso il basso.
 */
@Composable
fun FullPlayer(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val np = vm.nowPlaying ?: return
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var coverCenter by remember { mutableStateOf<Offset?>(null) }
    var coverSizePx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(300))
            onDismiss()
        }
    }
    BackHandler(enabled = !closing) { closing = true }

    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                translationY = drag
            }
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primaryContainer, scheme.surface, scheme.background)
                )
            )
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
        val screenH = with(density) { maxHeight.toPx() }
        val coverSide = minOf(maxWidth - 56.dp, maxHeight * 0.42f)
        // Punto di partenza: la copertina del mini player (in basso a sinistra).
        val startCenter = Offset(
            x = with(density) { 58.dp.toPx() },
            y = screenH - with(density) { 150.dp.toPx() }
        )
        val miniSidePx = with(density) { 52.dp.toPx() }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Barra in alto
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorphIconButton(onClick = { closing = true }, size = 44.dp) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Chiudi", Modifier.size(28.dp))
                }
                Text(
                    "In riproduzione",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                MorphIconButton(onClick = { vm.menuTrack = np.toTrack() }, size = 44.dp) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Altro")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Copertina (o testi) che cresce fino a schermo intero
            Box(
                Modifier
                    .size(coverSide)
                    .onGloballyPositioned {
                        coverCenter = Offset(
                            it.positionInRoot().x + it.size.width / 2f,
                            it.positionInRoot().y + it.size.height / 2f
                        )
                        coverSizePx = it.size.width.toFloat()
                    }
                    .graphicsLayer {
                        val p = progress.value
                        val c = coverCenter
                        if (c != null && coverSizePx > 0f) {
                            val s = lerp(miniSidePx / coverSizePx, 1f, p)
                            scaleX = s
                            scaleY = s
                            translationX = lerp(startCenter.x - c.x, 0f, p)
                            translationY = lerp(startCenter.y - c.y, 0f, p)
                        }
                    }
            ) {
                val playing = vm.wantsPlay
                val artScale by animateFloatAsState(
                    targetValue = if (playing) 1f else 0.9f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    label = "artScale"
                )
                val corner by animateDpAsState(
                    targetValue = if (playing) 36.dp else 72.dp,
                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                    label = "artCorner"
                )
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        (fadeIn(tween(300)) + scaleIn(initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = 0.92f))
                    },
                    label = "artOrLyrics"
                ) { lyricsMode ->
                    if (lyricsMode) {
                        LyricsPanel(vm, Modifier.fillMaxSize())
                    } else {
                        Artwork(
                            np.artwork,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                },
                            shape = RoundedCornerShape(corner),
                            px = 1000
                        )
                    }
                }
            }

            // Tutto il resto compare dopo che la copertina è cresciuta.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        val t = ((progress.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * 60.dp.toPx()
                    },
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            np.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            np.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LikeButton(liked = vm.store.isLiked(np.id), onClick = { vm.store.toggleLike(np.toTrack()) })
                }

                SeekBar(vm)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MorphIconButton(onClick = vm::previous, size = 68.dp, tonal = true) {
                        Icon(Icons.Rounded.SkipPrevious, "Precedente", Modifier.size(34.dp))
                    }
                    PlayPauseButton(vm, large = true)
                    MorphIconButton(onClick = vm::next, size = 68.dp, tonal = true) {
                        Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(34.dp))
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
                        label = { Text("Testi") }
                    )
                    Spacer(Modifier.width(12.dp))
                    MorphIconButton(onClick = { addTrack = np.toTrack() }, size = 48.dp, tonal = true) {
                        Icon(Icons.Rounded.Add, contentDescription = "Aggiungi a playlist")
                    }
                }
            }
        }
    }

    addTrack?.let { AddToPlaylistDialog(vm.store, it) { addTrack = null } }
}

/**
 * Pulsante Material 3 con animazione di forma: quando lo premi si schiaccia e passa da cerchio
 * a quadrato arrotondato, poi torna indietro con effetto molla.
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "morphScale"
    )
    val corner by animateIntAsState(
        targetValue = if (pressed) 28 else 50,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "morphCorner"
    )
    val shape = RoundedCornerShape(percent = corner)
    val mod = modifier
        .size(size)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    if (tonal) {
        FilledTonalIconButton(onClick = onClick, interactionSource = source, shape = shape, modifier = mod, content = content)
    } else {
        IconButton(onClick = onClick, interactionSource = source, modifier = mod, content = content)
    }
}
