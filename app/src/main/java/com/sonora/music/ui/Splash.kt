package com.sonora.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.music.R

/**
 * Splash dell'app: continua quella di sistema (stesso colore dinamico del telefono)
 * con logo e scritta bianchi, poi sfuma sull'app.
 */
@Composable
fun SplashOverlay(visible: Boolean) {
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(450))) {
        val base = colorResource(R.color.splash_bg)
        val accent = MaterialTheme.colorScheme.tertiary

        val pop = remember { Animatable(0.7f) }
        val textAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
        LaunchedEffect(Unit) {
            textAlpha.animateTo(1f, tween(500, delayMillis = 250))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(base, lerp(base, accent, 0.3f)))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(168.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                )
                Text(
                    "Sonora",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
                )
            }
        }
    }
}
