package com.sonora.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonora.music.Store
import com.sonora.music.Track
import kotlinx.coroutines.delay
import java.util.Locale

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

@Composable
fun Artwork(url: String?, modifier: Modifier, shape: Shape) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Tre palline che rimbalzano a ondate. */
@Composable
fun BouncyLoader() {
    val transition = rememberInfiniteTransition(label = "loader")
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEachIndexed { i, color ->
            val y by transition.animateFloat(
                initialValue = 0f,
                targetValue = -24f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140)
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(18.dp)
                    .offset(y = y.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Testo che si "scrive" lettera per lettera. Lo spazio è riservato fin dall'inizio
 * (le lettere non ancora scritte sono trasparenti), quindi il layout non salta.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = Color.Unspecified,
    enabled: Boolean = true,
    startDelay: Long = 0,
    charDelay: Long = 40,
    onDone: () -> Unit = {}
) {
    var count by remember(text) { mutableStateOf(0) }
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(text, enabled) {
        if (!enabled) return@LaunchedEffect
        count = 0
        delay(startDelay)
        for (i in 1..text.length) {
            count = i
            delay(charDelay)
        }
        currentOnDone()
    }

    val annotated = buildAnnotatedString {
        append(text.take(count))
        withStyle(SpanStyle(color = Color.Transparent)) { append(text.drop(count)) }
    }
    Text(text = annotated, modifier = modifier, style = style, textAlign = textAlign, color = color)
}

/**
 * Fa comparire un elemento con effetto molla (sale, si ingrandisce e rimbalza).
 * Lo spazio è riservato, quindi gli altri elementi non si spostano.
 */
@Composable
fun Reveal(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    delayMs: Long = 0,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            delay(delayMs)
            progress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            val s = 0.8f + 0.2f * p
            scaleX = s
            scaleY = s
            translationY = (1f - p) * 48.dp.toPx()
        }
    ) { content() }
}

/** Cuoricino che "pulsa" quando viene attivato. */
@Composable
fun LikeButton(liked: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    var initial by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (initial) {
            initial = false
        } else if (liked) {
            scale.snapTo(0.55f)
            scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
        }
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Rimuovi dai Mi piace" else "Mi piace",
            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    selected: Boolean,
    liked: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "rowPress"
    )
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 44.dp else 24.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "rowCorner"
    )

    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.thumb, Modifier.size(56.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = if (track.durationSec > 0) "${track.artist} · ${formatTime(track.durationSec * 1000)}" else track.artist
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LikeButton(liked = liked, onClick = onLike)
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.Add, contentDescription = "Aggiungi a playlist")
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Nome della playlist") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Crea") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
fun AddToPlaylistDialog(store: Store, track: Track, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NewPlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                val p = store.createPlaylist(name)
                store.addToPlaylist(p.id, track)
                onDismiss()
            }
        )
    } else {
        val mine = store.playlists.filter { !it.fromGoogle }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Aggiungi a playlist") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    item {
                        PlaylistChoice("+ Nuova playlist", null) { creating = true }
                    }
                    items(mine, key = { it.id }) { p ->
                        PlaylistChoice(p.name, "${p.tracks.size} brani") {
                            store.addToPlaylist(p.id, track)
                            onDismiss()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
        )
    }
}

@Composable
private fun PlaylistChoice(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
