package com.sonora.music.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.NotifHub
import com.sonora.music.NotifItem
import com.sonora.music.PlayerViewModel
import com.sonora.music.ArtistRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Cerca di capire se il dispositivo è un tablet: lato corto dello schermo di almeno 600 dp. */
@Composable
fun isTabletDevice(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600

@Composable
fun isLandscape(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

/** Sfondo: copertina sfocata e scurita. */
@Composable
private fun BlurBackdrop(art: String?, dim: Float) {
    Box(Modifier.fillMaxSize()) {
        if (art != null) {
            Artwork(art, Modifier.fillMaxSize().blur(50.dp), RectangleShape, px = 160)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

/**
 * Telefono in orizzontale: schermata stile AOD. Luminosità limitata (~38%), schermo sempre acceso,
 * a sinistra data, ora e notifiche; a destra il brano in riproduzione con i controlli.
 */
@Composable
fun AodScreen(vm: PlayerViewModel) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val old = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window?.let {
            val lp = it.attributes
            lp.screenBrightness = 0.38f
            it.attributes = lp
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = old
                it.attributes = lp
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(10_000)
        }
    }
    var access by remember { mutableStateOf(NotifHub.hasAccess(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            access = NotifHub.hasAccess(context)
            delay(3_000)
        }
    }

    // Alla comparsa di una notifica nuova: glow neon che gira attorno al suo rettangolo per qualche secondo.
    val seen = remember { mutableSetOf<String>() }
    var primed by remember { mutableStateOf(false) }
    var glowKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(1500)
        seen.addAll(NotifHub.items.map { it.key })
        primed = true
    }
    val keys = NotifHub.items.map { it.key }
    LaunchedEffect(keys, primed) {
        if (!primed) return@LaunchedEffect
        val fresh = keys.firstOrNull { it !in seen }
        seen.addAll(keys)
        if (fresh != null) {
            glowKey = fresh
            delay(6000)
            if (glowKey == fresh) glowKey = null
        }
    }

    val np = vm.nowPlaying
    val soft = Color.White.copy(alpha = 0.8f)

    CompositionLocalProvider(LocalContentColor provides soft) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BlurBackdrop(np?.artwork, dim = 0.7f)

            Row(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Sinistra: data, ora, notifiche
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY))
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = soft
                    )
                    Text(
                        now.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 72.sp
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!access) {
                        Text(
                            "Per vedere qui le notifiche serve il tuo consenso.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = soft
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Consenti accesso alle notifiche") }
                    } else if (NotifHub.items.isEmpty()) {
                        Text("Nessuna notifica", style = MaterialTheme.typography.bodyMedium, color = soft)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(NotifHub.items.toList(), key = { it.key }) {
                                NotifRow(it, glow = it.key == glowKey)
                            }
                        }
                    }
                }

                // Destra: brano
                Column(
                    Modifier.weight(1.1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (np == null) {
                        Text("Nessun brano in riproduzione", style = MaterialTheme.typography.titleMedium, color = soft)
                    } else {
                        Artwork(
                            np.artwork,
                            Modifier.fillMaxHeight(0.5f).aspectRatio(1f),
                            RoundedCornerShape(24.dp),
                            px = 600
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            np.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            np.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = soft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        SeekBar(vm, tint = soft)
                        PlayerControls(vm, compact = true, showSkip10 = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifRow(n: NotifItem, glow: Boolean) {
    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .then(if (glow) Modifier.neonGlow() else Modifier)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (glow) Color(0xFF0B0F1E) else Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (n.icon != null) {
                Image(
                    bitmap = n.icon,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Spacer(Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(n.app, n.title).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (n.text.isNotBlank()) {
                    Text(
                        n.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Pennello a gradiente conico che ruota: i colori neon "corrono" attorno al bordo. */
private class SpinBrush(private val angle: Float, private val colors: IntArray) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val shader = android.graphics.SweepGradient(cx, cy, colors, null)
        val m = android.graphics.Matrix()
        m.postRotate(angle, cx, cy)
        shader.setLocalMatrix(m)
        return shader
    }
}

private val NEON = intArrayOf(
    0xFF00F0FF.toInt(), 0xFF7A5CFF.toInt(), 0xFFFF2BD6.toInt(), 0xFF7A5CFF.toInt(), 0xFF00F0FF.toInt()
)

/** Glow neon circolare attorno al rettangolo: strati sempre più larghi e trasparenti attorno a un filo luminoso. */
private fun Modifier.neonGlow(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "neon")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "neonAngle"
    )
    val pulse = transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "neonPulse"
    )
    drawBehind {
        val brush = SpinBrush(angle.value, NEON)
        val corner = 16.dp.toPx()
        val layers = listOf(18.dp to 0.07f, 12.dp to 0.12f, 7.dp to 0.25f, 3.dp to 0.95f)
        for ((w, a) in layers) {
            val stroke = w.toPx()
            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(corner + stroke / 2f),
                style = Stroke(width = stroke),
                alpha = a * pulse.value
            )
        }
    }
}

/** Precedente / play-pausa / successivo (e ±10 secondi nella modalità compatta). */
@Composable
fun PlayerControls(vm: PlayerViewModel, compact: Boolean, showSkip10: Boolean) {
    val s = if (compact) 52.dp else 68.dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSkip10) {
            MorphIconButton(onClick = { vm.seekBy(-10_000) }, size = 44.dp) {
                Icon(Icons.Rounded.Replay10, contentDescription = "Indietro di 10 secondi")
            }
        }
        MorphIconButton(onClick = vm::previous, size = s) {
            Icon(Icons.Rounded.SkipPrevious, "Precedente", Modifier.size(32.dp))
        }
        PlayButton(vm, size = if (compact) 64.dp else 80.dp)
        MorphIconButton(onClick = vm::next, size = s) {
            Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(32.dp))
        }
        if (showSkip10) {
            MorphIconButton(onClick = { vm.seekBy(10_000) }, size = 44.dp) {
                Icon(Icons.Rounded.Forward10, contentDescription = "Avanti di 10 secondi")
            }
        }
    }
}

/** Tablet in orizzontale: pannello del brano in riproduzione sempre visibile a destra. */
@Composable
fun NowPlayingPane(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val np = vm.nowPlaying
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(modifier.background(Color.Black)) {
            BlurBackdrop(np?.artwork, dim = 0.55f)
            if (np == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Scegli un brano per iniziare", color = Color.White.copy(alpha = 0.75f))
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Artwork(
                        np.artwork,
                        Modifier.fillMaxWidth().aspectRatio(1f),
                        RoundedCornerShape(36.dp),
                        px = 900
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        np.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        np.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                ArtistRepository.splitArtists(np.artist).firstOrNull()?.let { vm.artistOpen = it }
                            }
                    )
                    Spacer(Modifier.height(8.dp))
                    SeekBar(vm, tint = Color.White)
                    Spacer(Modifier.height(8.dp))
                    PlayerControls(vm, compact = false, showSkip10 = false)
                }
            }
        }
    }
}
