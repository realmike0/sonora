package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sonora.music.HeadsetInfo
import com.sonora.music.Headsets
import com.sonora.music.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FRAMES = 48

/**
 * Schermata che compare quando l'app è aperta e si collega (o è già collegato) un dispositivo audio:
 * modello 3D nero che gira in loop, scelto in base al tipo (auricolari, cuffie, speaker, auto),
 * e il pulsante "Inizia ad ascoltare".
 */
@Composable
fun DeviceSheet(vm: PlayerViewModel) {
    val info = vm.headset
    val visible = info != null && !vm.headsetDismissed && !vm.wantsPlay
    if (visible) BackHandler { vm.headsetDismissed = true }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { vm.headsetDismissed = true }
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            if (info != null) DeviceCard(vm, info)
        }
    }
}

@Composable
private fun DeviceCard(vm: PlayerViewModel, info: HeadsetInfo) {
    Surface(
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* assorbe i tocchi: non chiude */ }
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF232634), Color(0xFF12131A))))
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Turntable(info)
            Spacer(Modifier.height(4.dp))
            Text(
                info.kind.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                info.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(22.dp))
            ExpressiveButton(
                label = "Inizia ad ascoltare",
                onClick = {
                    vm.headsetDismissed = true
                    vm.startFavorites()
                }
            )
            TextButton(onClick = { vm.headsetDismissed = true }) {
                Text("Non ora", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/** Modello 3D che gira: fotogrammi pre-renderizzati riprodotti in loop (nero, scelto a caso tra le varianti). */
@Composable
private fun Turntable(info: HeadsetInfo) {
    val context = LocalContext.current
    val variant = remember(info) { (1..2).random() }
    val frames by produceState<List<ImageBitmap>>(emptyList(), info, variant) {
        value = withContext(Dispatchers.Default) {
            (0 until FRAMES).mapNotNull { i ->
                val id = Headsets.frameRes(context, info.kind, variant, i)
                if (id == 0) null else android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "turntable")
    val t = transition.animateFloat(
        initialValue = 0f,
        targetValue = FRAMES.toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "turn"
    )
    Canvas(Modifier.size(250.dp)) {
        val list = frames
        if (list.isNotEmpty()) {
            val img = list[t.value.toInt().coerceIn(0, list.lastIndex) % list.size]
            drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}

/** Pulsante in stile Material 3 Expressive: pillola grande che si schiaccia e cambia forma quando lo premi. */
@Composable
fun ExpressiveButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
        label = "xbScale"
    )
    val corner by animateIntAsState(
        targetValue = if (pressed) 26 else 50,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "xbCorner"
    )
    Button(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(percent = corner.coerceIn(0, 50)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DFF), contentColor = Color.White),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}
