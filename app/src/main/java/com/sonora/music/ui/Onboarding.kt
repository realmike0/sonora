package com.sonora.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonora.music.PlayerViewModel
import com.sonora.music.SyncState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Gradienti sfocati che si muovono lentamente, con i colori Material You del telefono. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(24000, easing = LinearEasing)),
        label = "phase"
    )

    Box(modifier.fillMaxSize().background(lerp(scheme.primary, Color.Black, 0.72f))) {
        Canvas(Modifier.fillMaxSize().blur(48.dp)) {
            val w = size.width
            val h = size.height

            fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                val center = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center, radius),
                    radius = radius,
                    center = center
                )
            }

            blob(scheme.primary.copy(alpha = 0.55f), w * (0.25f + 0.15f * sin(phase)), h * (0.2f + 0.1f * cos(phase)), w * 0.8f)
            blob(scheme.tertiary.copy(alpha = 0.5f), w * (0.8f + 0.12f * cos(phase * 2)), h * (0.35f + 0.12f * sin(phase * 2)), w * 0.75f)
            blob(scheme.secondary.copy(alpha = 0.5f), w * (0.3f + 0.2f * sin(phase * 3 + 1f)), h * (0.8f + 0.08f * cos(phase * 3)), w * 0.9f)
            blob(scheme.primaryContainer.copy(alpha = 0.7f), w * (0.85f + 0.1f * cos(phase + 2f)), h * (0.9f + 0.06f * sin(phase)), w * 0.6f)
        }
        // Velo leggero per mantenere il testo leggibile.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f))
                    )
                )
        )
    }
}

@Composable
fun OnboardingScreen(vm: PlayerViewModel) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }

    // Appena parte l'importazione (dopo il login) passiamo alla schermata di avanzamento.
    LaunchedEffect(vm.syncState) {
        if (vm.syncState == SyncState.Running) step = 2
        if (vm.syncState == SyncState.Done) {
            delay(1500)
            vm.finishOnboarding(name)
        }
    }

    // Testo chiaro tinto con il colore dinamico del telefono (leggibile sullo sfondo scuro).
    val light = lerp(Color.White, MaterialTheme.colorScheme.primary, 0.22f)

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        CompositionLocalProvider(LocalContentColor provides light) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(500, delayMillis = 150)) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                        )) togetherWith (fadeOut(tween(250)) + scaleOut(targetScale = 1.06f))
                },
                label = "step"
            ) { s ->
                when (s) {
                    0 -> NameStep(
                        name = name,
                        onName = { name = it },
                        onNext = { if (name.isNotBlank()) step = 1 }
                    )

                    1 -> GoogleStep(
                        name = name,
                        onConnect = { vm.loginOpen = true },
                        onSkip = { vm.finishOnboarding(name) }
                    )

                    else -> SyncStep(vm = vm, name = name)
                }
            }
        }
        }
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    var titleDone by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TypewriterText(
            text = "Ciao! Come ti chiami?",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
            startDelay = 400,
            onDone = { titleDone = true }
        )
        Spacer(Modifier.height(28.dp))
        Reveal(enabled = titleDone, modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = onName,
                singleLine = true,
                placeholder = { Text("Il tuo nome") },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = LocalContentColor.current.copy(alpha = 0.16f),
                    unfocusedContainerColor = LocalContentColor.current.copy(alpha = 0.12f),
                    focusedTextColor = LocalContentColor.current,
                    unfocusedTextColor = LocalContentColor.current,
                    cursorColor = LocalContentColor.current,
                    focusedPlaceholderColor = LocalContentColor.current.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = LocalContentColor.current.copy(alpha = 0.6f)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onNext() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Reveal(enabled = titleDone, delayMs = 250, modifier = Modifier.fillMaxWidth()) {
            Button(
                colors = onbButtonColors(),
                onClick = onNext,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Continua") }
        }
        Spacer(Modifier.height(12.dp))
        Reveal(enabled = titleDone, delayMs = 500) {
            Text(
                "Te lo chiediamo una volta sola.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun GoogleStep(name: String, onConnect: () -> Unit, onSkip: () -> Unit) {
    var greetingDone by remember { mutableStateOf(false) }
    var questionDone by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TypewriterText(
            text = "Piacere, ${name.trim()}!",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
            startDelay = 300,
            onDone = { greetingDone = true }
        )
        Spacer(Modifier.height(16.dp))
        TypewriterText(
            text = "Vuoi collegare il tuo account Google per sincronizzare tutte le tue playlist?",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            enabled = greetingDone,
            startDelay = 150,
            charDelay = 22,
            onDone = { questionDone = true }
        )
        Spacer(Modifier.height(32.dp))
        Reveal(enabled = questionDone, modifier = Modifier.fillMaxWidth()) {
            Button(
                colors = onbButtonColors(),
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Collega account Google") }
        }
        Spacer(Modifier.height(8.dp))
        Reveal(enabled = questionDone, delayMs = 200) {
            TextButton(onClick = onSkip, colors = onbTextButtonColors()) { Text("Salta") }
        }
        Spacer(Modifier.height(8.dp))
        Reveal(enabled = questionDone, delayMs = 400) {
            Text(
                "Se salti, creiamo un profilo locale: Mi piace e playlist restano su questo telefono.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SyncStep(vm: PlayerViewModel, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (vm.syncState) {
            SyncState.Failed -> {
                TypewriterText(
                    text = "Ops, qualcosa è andato storto",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    vm.syncError ?: "Sincronizzazione non riuscita",
                    style = MaterialTheme.typography.bodyMedium,
                    color = lerp(Color.White, MaterialTheme.colorScheme.error, 0.4f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Reveal(delayMs = 600, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        colors = onbButtonColors(),
                        onClick = { vm.syncGoogle() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Riprova") }
                }
                Reveal(delayMs = 800) {
                    TextButton(colors = onbTextButtonColors(), onClick = {
                        vm.disconnectGoogle()
                        vm.finishOnboarding(name)
                    }) { Text("Continua senza account Google") }
                }
            }

            SyncState.Done -> {
                TypewriterText(
                    text = "Tutto pronto, ${name.trim()}!",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                TypewriterText(
                    text = "Importo le tue playlist…",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(40.dp))
                BouncyLoader()
                Spacer(Modifier.height(32.dp))
                Text(
                    vm.syncStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Pulsanti chiari con testo scuro tinto dinamico: restano leggibili sullo sfondo scuro. */
@Composable
private fun onbButtonColors() = ButtonDefaults.buttonColors(
    containerColor = LocalContentColor.current,
    contentColor = lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.65f),
    disabledContainerColor = LocalContentColor.current.copy(alpha = 0.25f),
    disabledContentColor = LocalContentColor.current.copy(alpha = 0.5f)
)

@Composable
private fun onbTextButtonColors() = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
