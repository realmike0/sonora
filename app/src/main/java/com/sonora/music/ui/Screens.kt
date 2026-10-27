@file:OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.sonora.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import com.sonora.music.PlayerViewModel
import com.sonora.music.Track
import com.sonora.music.toTrack
import kotlin.math.PI
import kotlin.math.sin

// Molla "gommosa" usata da quasi tutte le animazioni.
private const val BOUNCY = Spring.DampingRatioMediumBouncy

/** Radice dell'app: onboarding al primo avvio, poi l'app vera. Il login Google sta sopra a tutto. */
@Composable
fun SonoraRoot(vm: PlayerViewModel) {
    var splash by rememberSaveable { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        splash = false
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = vm.store.onboarded,
            animationSpec = tween(700),
            label = "root"
        ) { done ->
            if (done) SonoraScreen(vm) else OnboardingScreen(vm)
        }

        AnimatedVisibility(
            visible = vm.settingsOpen && vm.store.onboarded,
            enter = slideInVertically(spring(BOUNCY, Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut()
        ) {
            BackHandler { vm.settingsOpen = false }
            SettingsScreen(vm, onClose = { vm.settingsOpen = false })
        }

        AnimatedVisibility(
            visible = vm.loginOpen,
            enter = slideInVertically(spring(BOUNCY, Spring.StiffnessLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            GoogleLoginScreen(
                onCookie = { vm.connectGoogle(it) },
                onClose = { vm.loginOpen = false }
            )
        }

        SplashOverlay(visible = splash)
    }
}

@Composable
fun SonoraScreen(vm: PlayerViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    AskNotificationPermission()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(vm) }
    ) { padding ->
        // Altezza di mini player + barra fluttuante: serve alle liste per non finirci sotto.
        var overlayPx by remember { mutableIntStateOf(0) }
        val bottomPad = with(LocalDensity.current) { overlayPx.toDp() }

        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(100))
                },
                label = "tabs"
            ) { t ->
                when (t) {
                    0 -> HomeScreen(vm, bottomPad)
                    1 -> SearchContent(vm, bottomPad)
                    else -> LibraryScreen(vm, bottomPad)
                }
            }

            val bg = MaterialTheme.colorScheme.background
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayPx = it.height }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.92f))))
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 12.dp)
            ) {
                AnimatedVisibility(
                    visible = vm.nowPlaying != null,
                    enter = slideInVertically(spring(BOUNCY, Spring.StiffnessLow)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    MiniPlayer(vm, onOpen = { vm.playerOpen = true })
                }
                FloatingNavBar(selected = tab, onSelect = { tab = it })
            }
        }
    }

    if (vm.playerOpen && vm.nowPlaying != null) {
        PlayerSheet(vm, onDismiss = { vm.playerOpen = false })
    }

    vm.menuTrack?.let { track ->
        TrackMenuSheet(vm, track, onDismiss = { vm.menuTrack = null })
    }
}

/** Barra in alto: nome dell'app a sinistra, foto profilo (apre le impostazioni) a destra. */
@Composable
private fun TopBar(vm: PlayerViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Sonora",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.weight(1f)
        )
        ProfileButton(vm)
    }
}

@Composable
private fun ProfileButton(vm: PlayerViewModel) {
    val photo = vm.store.accountPhoto
    val initial = vm.store.userName.trim().firstOrNull()?.uppercase() ?: "S"
    Surface(
        onClick = { vm.settingsOpen = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photo != null) {
                Artwork(photo, Modifier.fillMaxSize(), CircleShape, px = 128)
            } else {
                Text(
                    initial,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Android 13+: serve il permesso per mostrare la notifica dei controlli multimediali. */
@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// ───────────────────────── Ricerca ─────────────────────────

@Composable
private fun SearchContent(vm: PlayerViewModel, bottomPad: Dp, modifier: Modifier = Modifier) {
    val store = vm.store

    Column(modifier.fillMaxSize()) {
        SearchField(vm)

        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BouncyLoader()
            }

            // Risultati della ricerca in corso.
            vm.results.isNotEmpty() && vm.query.isNotBlank() -> TrackList(vm, vm.results, fromSearch = true, bottomPad = bottomPad)

            vm.error != null && vm.query.isNotBlank() -> Message(vm.error ?: "")

            // Mai cercato nulla: testo di sempre.
            !store.hasSearched -> Message("Cerca un brano o un artista per iniziare")

            // Ha già cercato: i brani riprodotti dopo le ricerche.
            store.searchPlays.isEmpty() -> Message("Avvia un brano dai risultati e lo ritroverai qui")

            else -> Column(Modifier.fillMaxSize()) {
                Text(
                    "Riprodotti dopo le tue ricerche",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
                )
                TrackList(vm, store.searchPlays.toList(), fromSearch = false, bottomPad = bottomPad)
            }
        }
    }
}

@Composable
private fun TrackList(vm: PlayerViewModel, tracks: List<Track>, fromSearch: Boolean, bottomPad: Dp) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                selected = vm.nowPlaying?.id == track.id,
                liked = vm.store.isLiked(track.id),
                onClick = { vm.play(tracks, index, fromSearch) },
                onLike = { vm.store.toggleLike(track) },
                onMore = { vm.menuTrack = track },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun SearchField(vm: PlayerViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = vm.query,
        onValueChange = { vm.query = it },
        placeholder = { Text("Cerca brani, artisti…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (vm.query.isNotEmpty()) {
            {
                IconButton(onClick = { vm.query = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancella")
                }
            }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            vm.search()
            keyboard?.hide()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

// ───────────────────────── Player ─────────────────────────

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val np = vm.nowPlaying ?: return
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Column {
            Row(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(np.artwork, Modifier.size(52.dp), RoundedCornerShape(16.dp), px = 192)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        np.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        np.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayPauseButton(vm, large = false)
            }
            WavyProgress(
                progress = { vm.progress },
                playing = vm.wantsPlay,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }
    }
}

/**
 * Barra di avanzamento a onda: ondeggia mentre suona e si "spiana" in pausa.
 * Per non pesare sui telefoni meno potenti l'animazione gira solo durante la riproduzione
 * e avanzamento/fase sono letti in fase di disegno (nessuna ricomposizione).
 */
@Composable
private fun WavyProgress(progress: () -> Float, playing: Boolean, modifier: Modifier = Modifier) {
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.18f)

    val phase = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                phase.animateTo(
                    phase.value + (2 * PI).toFloat(),
                    tween(1400, easing = LinearEasing)
                )
            }
        }
    }
    val amp by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = spring(BOUNCY, Spring.StiffnessVeryLow),
        label = "amp"
    )

    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val stroke = 4.dp.toPx()
        val amplitude = 5.dp.toPx() * amp
        val wavelength = 32.dp.toPx()
        val cy = size.height / 2f
        val endX = (size.width * progress()).coerceIn(0f, size.width)
        val phase = phase.value

        drawLine(
            color = track,
            start = Offset(endX, cy),
            end = Offset(size.width, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        if (endX > 0f) {
            val path = Path()
            val step = 3.dp.toPx()
            var x = 0f
            path.moveTo(0f, cy + sin(-phase) * amplitude)
            while (x < endX) {
                x = (x + step).coerceAtMost(endX)
                path.lineTo(x, cy + sin(x / wavelength * 2f * PI.toFloat() - phase) * amplitude)
            }
            drawPath(
                path = path,
                color = active,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
private fun PlayerSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val np = vm.nowPlaying ?: return
    val playing = vm.wantsPlay
    var showLyrics by remember { mutableStateOf(false) }
    var addTrack by remember { mutableStateOf<Track?>(null) }

    // La copertina "respira": si rimpicciolisce e si arrotonda in pausa.
    val artScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(BOUNCY, Spring.StiffnessLow),
        label = "artScale"
    )
    val artCorner by animateDpAsState(
        targetValue = if (playing) 40.dp else 96.dp,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label = "artCorner"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.9f))
                },
                label = "artOrLyrics"
            ) { lyricsMode ->
                if (lyricsMode) {
                    LyricsPanel(vm, Modifier.fillMaxWidth(0.88f).aspectRatio(1f))
                } else {
                    Box(Modifier.fillMaxWidth(0.88f).aspectRatio(1f)) {
                        Artwork(
                            np.artwork,
                            px = 900,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                },
                            shape = RoundedCornerShape(artCorner.coerceAtLeast(0.dp))
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                np.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                np.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LikeButton(liked = vm.store.isLiked(np.id), onClick = { vm.store.toggleLike(np.toTrack()) })
                FilterChip(
                    selected = showLyrics,
                    onClick = { showLyrics = !showLyrics },
                    label = { Text("Testi") }
                )
                IconButton(onClick = { addTrack = np.toTrack() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Aggiungi a playlist")
                }
            }

            SeekBar(vm)

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BouncyTonalButton(onClick = vm::previous, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Precedente", Modifier.size(32.dp))
                }
                PlayPauseButton(vm, large = true)
                BouncyTonalButton(onClick = vm::next, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    addTrack?.let { AddToPlaylistDialog(vm.store, it) { addTrack = null } }
}

/** Barra di avanzamento isolata: si aggiorna 4 volte al secondo senza ricomporre tutto il player. */
@Composable
private fun SeekBar(vm: PlayerViewModel) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    Slider(
        value = dragging ?: vm.progress,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let(vm::seekToFraction)
            dragging = null
        }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val shown = ((dragging ?: vm.progress) * vm.durationMs).toLong()
        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium)
        Text(formatTime(vm.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Play/pausa: la forma passa da cerchio a quadrato smussato con effetto molla,
 * si schiaccia quando lo premi e l'icona "scoppia" dentro e fuori.
 */
@Composable
private fun PlayPauseButton(vm: PlayerViewModel, large: Boolean) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "playPress"
    )
    val cornerPercent by animateIntAsState(
        targetValue = if (vm.wantsPlay) 32 else 50,
        animationSpec = spring(BOUNCY, Spring.StiffnessLow),
        label = "playMorph"
    )

    FilledIconButton(
        onClick = vm::togglePlay,
        interactionSource = source,
        shape = RoundedCornerShape(percent = cornerPercent.coerceIn(0, 50)),
        modifier = Modifier
            .size(if (large) 88.dp else 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AnimatedContent(
            targetState = vm.wantsPlay,
            transitionSpec = {
                (scaleIn(spring(BOUNCY)) + fadeIn()) togetherWith (scaleOut() + fadeOut())
            },
            label = "playIcon"
        ) { isPlaying ->
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                modifier = Modifier.size(if (large) 44.dp else 28.dp)
            )
        }
    }
}

/** Pulsante tonale che rimbalza quando lo premi. */
@Composable
private fun BouncyTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.8f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "btnPress"
    )
    FilledTonalIconButton(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}
