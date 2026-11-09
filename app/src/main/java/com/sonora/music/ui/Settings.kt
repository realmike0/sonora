package com.sonora.music.ui

import android.os.Build
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Mail
import com.sonora.music.SonoraId
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilterChip
import com.sonora.music.AudioStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sonora.music.LyricsRepository
import com.sonora.music.PlayerViewModel
import com.sonora.music.Store
import com.sonora.music.SyncState

@OptIn(ExperimentalLayoutApi::class, androidx.media3.common.util.UnstableApi::class)
@Composable
fun SettingsScreen(vm: PlayerViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val start = remember { vm.settingsStart.also { vm.settingsStart = null } }
    var page by remember { mutableStateOf<String?>(if (start != null) "inbox" else null) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, modifier = modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(
            targetState = page,
            transitionSpec = {
                val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                if (targetState != null) {
                    (androidx.compose.animation.slideInHorizontally(spec) { it / 4 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)))
                        .togetherWith(androidx.compose.animation.slideOutHorizontally(spec) { -it / 6 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
                } else {
                    (androidx.compose.animation.slideInHorizontally(spec) { -it / 6 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)))
                        .togetherWith(androidx.compose.animation.slideOutHorizontally(spec) { it / 4 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
                }
            },
            label = "settings"
        ) { p ->
            if (p == null) SettingsHub(vm, onClose, open = { page = it })
            else SettingsPage(vm, p, onBack = { page = null }, startNew = start == "ticket-new")
        }
    }
}

private class SettingsNotes(val glyph: String, val angle: Float, val dist: Float, val size: Float)

@Composable
private fun SettingsHub(vm: PlayerViewModel, onClose: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val store = vm.store
    val installed = remember { com.sonora.music.Framework.installed(context) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 64.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Indietro")) }
            Text(tr("Impostazioni"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        SettingsHeader(store.userName.ifBlank { "Ospite" }, installed)
        Spacer(Modifier.height(12.dp))
        androidx.compose.runtime.LaunchedEffect(SonoraId.signedIn) {
            if (SonoraId.signedIn) runCatching { SonoraId.notices(context) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            Button(onClick = { if (installed) com.sonora.music.Framework.manage(context) else open("manage") }) {
                Icon(Icons.Rounded.ManageAccounts, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Gestisci"))
            }
            androidx.compose.material3.FilledTonalButton(onClick = { open("inbox") }) {
                androidx.compose.material3.BadgedBox(badge = {
                    if (SonoraId.unread > 0) androidx.compose.material3.Badge { Text(SonoraId.unread.toString()) }
                }) { Icon(Icons.Rounded.Mail, null) }
                Spacer(Modifier.width(8.dp))
                Text(tr("Posta in arrivo"))
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val rows = listOf(
                Triple("style", Icons.Rounded.Palette, tr("Style")),
                Triple("player", Icons.Rounded.MusicNote, tr("Player")),
                Triple("advanced", Icons.Rounded.Tune, tr("Advanced")),
                Triple("languages", Icons.Rounded.Language, tr("Languages")),
                Triple("info", Icons.Rounded.Info, tr("Info"))
            )
            rows.forEachIndexed { i, (id, icon, label) ->
                val r = 22.dp
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) r else 6.dp, topEnd = if (i == 0) r else 6.dp,
                    bottomStart = if (i == rows.lastIndex) r else 6.dp, bottomEnd = if (i == rows.lastIndex) r else 6.dp
                )
                Surface(
                    onClick = { open(id) },
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Intestazione animata: per 3 secondi il cerchio "biscotto" gira rallentando e le note schizzano fuori;
 * poi tutto rientra in 0,5 secondi e resta un cerchio normale con la foto profilo del Framework.
 */
@Composable
private fun SettingsHeader(name: String, hasFramework: Boolean) {
    val context = LocalContext.current
    val t = remember { androidx.compose.animation.core.Animatable(0f) }      // 0..1 durante i 3 secondi
    val settle = remember { androidx.compose.animation.core.Animatable(0f) } // 0..1 durante i 0,5 secondi
    androidx.compose.runtime.LaunchedEffect(Unit) {
        t.animateTo(1f, tween(3000, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        settle.animateTo(1f, tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val notes = remember {
        listOf("♪", "♫", "♬", "♩", "♪", "♫", "♬", "♩", "♪", "♫").mapIndexed { i, g ->
            SettingsNotes(g, i * 36f + (i % 3) * 7f, 0.55f + (i % 4) * 0.12f, 18f + (i % 3) * 8f)
        }
    }
    val amp = 0.06f * (1f - settle.value)
    val rotation = t.value * 1080f
    val shape = remember(amp, rotation) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                val path = androidx.compose.ui.graphics.Path()
                val cx = size.width / 2f; val cy = size.height / 2f
                val r = minOf(cx, cy) / 1.06f
                val rot = Math.toRadians(rotation.toDouble()).toFloat()
                for (i in 0..180) {
                    val a = (i / 180f) * 2f * Math.PI.toFloat()
                    val rr = r * (1f + amp * kotlin.math.cos(9 * (a - rot)))
                    val x = cx + rr * kotlin.math.cos(a); val y = cy + rr * kotlin.math.sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
        // le note: escono con il giro (rallentando) e rientrano nel cerchio alla fine
        val out = (1f - (1f - t.value) * (1f - t.value)) * (1f - settle.value)
        val fade = if (settle.value > 0f) (1f - settle.value) else 1f
        notes.forEach { n ->
            val rad = Math.toRadians((n.angle + rotation * 0.25f).toDouble())
            val d = 150f * n.dist * out
            Text(
                n.glyph,
                color = color.copy(alpha = fade * 0.9f),
                fontSize = n.size.sp,
                modifier = Modifier.graphicsLayer {
                    translationX = (kotlin.math.cos(rad) * d).toFloat() * density
                    translationY = (kotlin.math.sin(rad) * d).toFloat() * density
                    rotationZ = (n.angle + rotation * 0.5f)
                    alpha = if (out < 0.02f) 0f else 1f
                }
            )
        }
        Box(Modifier.size(150.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (hasFramework) {
                coil.compose.AsyncImage(
                    model = remember { com.sonora.music.Framework.avatarUri },
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    Text(
        name,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@OptIn(ExperimentalLayoutApi::class, androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SettingsPage(vm: PlayerViewModel, page: String, onBack: () -> Unit, startNew: Boolean = false) {
    val store = vm.store
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    }
    val title = when (page) {
        "manage" -> tr("Gestisci"); "inbox" -> tr("Posta in arrivo"); "style" -> tr("Style"); "player" -> tr("Player")
        "advanced" -> tr("Advanced"); "languages" -> tr("Languages"); else -> tr("Info")
    }
    if (page == "inbox") {
        // La posta ha il suo scorrimento (e la chat il campo fisso sopra la tastiera): niente scroll della pagina.
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Indietro")) }
                Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold))
            }
            InboxPage(startNew, Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Indietro")) }
            Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold))
        }
        when (page) {
            "manage" -> SettingsCard(tr("Profilo")) {
                GoogleSection(vm)
                SonoraIdSection(vm)
            }
            "style" -> {
                SettingsCard(tr("Colori")) { ThemeSection() }
                SettingsCard(tr("Stile")) { StyleSection() }
            }
            "player" -> {
                SettingsCard(tr("Testi")) { LyricsSourcePicker(store) }
                SettingsCard(tr("Qualità audio")) { AudioQualitySection() }
                SettingsCard(tr("Equalizzatore")) { EqualizerSection() }
                SettingsCard(tr("Dissolvenza avanzata")) { FadeSection() }
            }
            "advanced" -> {
                if (java.time.LocalDate.now().monthValue == 12) {
                    SettingsCard(tr("Wrapped e tracciamento")) {
                        TrackingSection(onWrapped = { vm.wrappedOpen = true; vm.settingsOpen = false })
                    }
                }
                if (!com.sonora.music.ModNotify.fullScreenAllowed(context)) {
                    SettingsCard(tr("Avvisi a schermo intero")) {
                        Text(tr("Permetti a Sonora di mostrare gli avvisi importanti (reclami, sospensioni) a schermo intero."), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { com.sonora.music.ModNotify.askFullScreen(context) }) { Text(tr("Permetti")) }
                    }
                }
                SettingsCard(tr("Prestazioni")) { PerfSection() }
                SettingsCard(tr("Tocco")) { HapticsSection(); AudioHapticsSection() }
                SettingsCard(tr("Sempre attiva")) { KeepAliveSection() }
                SettingsCard(tr("Memoria e cache")) { CacheSection() }
            }
            "languages" -> SettingsCard(tr("Lingua")) { LanguageSection() }
            else -> {
                SettingsCard(tr("Informazioni")) { Text("Sonora $version", style = MaterialTheme.typography.titleMedium) }
                SettingsCard(tr("Aggiornamenti")) { UpdateSection(vm, version) }
                SettingsCard(tr("Community")) {
                    Text(tr("Novità, aggiornamenti e supporto sul canale Telegram di Sonora."), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { uriHandler.openUri("https://t.me/sonoraclient") }) { Text(tr("Apri su Telegram")) }
                    Text(tr("t.me/sonoraclient"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Watermark(Modifier.padding(top = 8.dp, bottom = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSection() {
    val context = LocalContext.current
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (canDynamic) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Colori dinamici"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Usa i colori dello sfondo del telefono"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = AppTheme.dynamic, onCheckedChange = { AppTheme.setDynamic(context, it) })
        }
    }
    if (!canDynamic || !AppTheme.dynamic) {
        Text(tr("Scegli il colore"), style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ColorWheel(AppTheme.hue) { AppTheme.setHue(context, it) }
        }
    }
    Text(tr("Tema"), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("light" to tr("Chiaro"), "dark" to tr("Scuro"), "system" to tr("Come il dispositivo")).forEach { (id, label) ->
            FilterChip(selected = AppTheme.mode == id, onClick = { AppTheme.setMode(context, id) }, label = { Text(label) })
        }
    }
}

/** Selettore di colore circolare: anello con tutte le tonalità e un cursore da trascinare. */
@Composable
private fun ColorWheel(hue: Float, onHue: (Float) -> Unit) {
    val size = 220.dp
    val preview = seedScheme(hue, false).primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                fun upd(o: androidx.compose.ui.geometry.Offset) {
                    val dx = o.x - this.size.width / 2f; val dy = o.y - this.size.height / 2f
                    var d = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                    if (d < 0) d += 360f
                    onHue(d)
                }
                detectDragGestures(onDragStart = { upd(it) }) { c, _ -> upd(c.position) }
            }.pointerInput(Unit) {
                detectTapGestures { o ->
                    val dx = o.x - this.size.width / 2f; val dy = o.y - this.size.height / 2f
                    var d = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                    if (d < 0) d += 360f
                    onHue(d)
                }
            }
        ) {
            val stroke = 34.dp.toPx()
            val r = this.size.minDimension / 2f - stroke / 2f
            val colors = (0..12).map { Color.hsl((it * 30f) % 360f, 0.75f, 0.55f) }
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.sweepGradient(colors, center),
                radius = r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
            )
            val a = Math.toRadians(hue.toDouble())
            val p = androidx.compose.ui.geometry.Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
            drawCircle(Color.White, radius = stroke / 2f + 4.dp.toPx(), center = p)
            drawCircle(Color.hsl(hue, 0.75f, 0.55f), radius = stroke / 2f - 2.dp.toPx(), center = p)
        }
        Box(Modifier.size(84.dp).clip(CookieShape).background(preview))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(AppStyle.card),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = AppStyle.cardAlpha),
        border = if (AppStyle.id == StyleId.GLASS) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun MixSwitch() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sonora", android.content.Context.MODE_PRIVATE) }
    var on by remember { mutableStateOf(prefs.getBoolean("mix_fade", true)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Mixaggio fluido"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Il brano finisce con una dissolvenza e il successivo entra in sfumatura."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = {
                on = it
                prefs.edit().putBoolean("mix_fade", it).apply()
            }
        )
    }
}

@Composable
private fun KeepAliveSection() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(com.sonora.music.Headsets.keepAliveEnabled(context)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Riconosci cuffie e speaker"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = {
                on = it
                com.sonora.music.Headsets.setKeepAlive(context, it)
                // Il permesso si chiede quando si scopre la funzione, una volta sola.
                val prefs = context.getSharedPreferences("sonora", android.content.Context.MODE_PRIVATE)
                if (it && !prefs.getBoolean("battery_asked", false)) {
                    prefs.edit().putBoolean("battery_asked", true).apply()
                    try {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                    }
                }
            }
        )
    }
}

@Composable
private fun UpdateSection(vm: PlayerViewModel, version: String) {
    Text("Versione installata: $version", style = MaterialTheme.typography.bodyMedium)
    val rel = vm.update
    if (rel != null) {
        Text(
            "Disponibile: ${rel.tag.removePrefix("v")}",
            style = MaterialTheme.typography.titleMedium
        )
        val progress = vm.updateProgress
        if (progress != null) {
            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { vm.downloadUpdate() }, enabled = progress == null) {
            Text(if (progress == null) "Scarica e installa" else "Scarico… ${(progress * 100).toInt()}%")
        }
    }
    vm.updateMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(onClick = { vm.checkUpdate(manual = true) }, enabled = !vm.checkingUpdate) {
        Text(if (vm.checkingUpdate) "Controllo…" else "Controlla aggiornamenti")
    }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sonora", android.content.Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean("auto_update_check", true)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Controllo automatico"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Switch(
            checked = auto,
            onCheckedChange = {
                auto = it
                prefs.edit().putBoolean("auto_update_check", it).apply()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleSection() {
    val context = LocalContext.current
    val suggested = remember { AppStyle.suggested() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleId.values().forEach { st ->
            FilterChip(
                selected = AppStyle.id == st,
                onClick = { AppStyle.set(context, st) },
                label = { Text(st.label + if (st == suggested) " ★" else "") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.options.forEach { (code, label) ->
            FilterChip(
                selected = Lang.code == code,
                onClick = { Lang.set(context, code) },
                label = { Text(if (code == "auto") tr("Automatica") else label) }
            )
        }
    }
}

@Composable
private fun LyricsSourcePicker(store: Store) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(tr("Fonte predefinita"), style = MaterialTheme.typography.labelMedium)
                    Text(store.lyricsSource, style = MaterialTheme.typography.titleMedium)
                }
                Text("▾", style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LyricsRepository.sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source) },
                    onClick = {
                        store.updateLyricsSource(source)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GoogleSection(vm: PlayerViewModel) {
    val store = vm.store

    if (store.googleCookie == null) {
        Button(onClick = { vm.loginOpen = true }) { Text(tr("Collega account Google")) }
        return
    }

    when (vm.syncState) {
        SyncState.Running -> {
            Text(
                vm.syncStatus.ifBlank { "Sincronizzazione in corso…" },
                style = MaterialTheme.typography.bodyMedium
            )
            BouncyLoader()
        }

        SyncState.Failed -> Text(
            vm.syncError ?: "Sincronizzazione non riuscita",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        SyncState.Done -> Text(
            tr("Playlist sincronizzate"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SyncState.Idle -> Unit
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { vm.syncGoogle() }, enabled = vm.syncState != SyncState.Running) {
            Text(tr("Sincronizza ora"))
        }
        OutlinedButton(onClick = { vm.disconnectGoogle() }) { Text(tr("Scollega")) }
    }
}

/** Filigrana in basso a sinistra: "GitHub" apre la pagina di realmike0. */
@Composable
private fun Watermark(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            tr("Made with ❤️ by realmike0 on"),
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "GitHub",
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            textDecoration = TextDecoration.Underline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://www.github.com/realmike0") }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.media3.common.util.UnstableApi::class)
@Composable
private fun CacheSection() {
    val limit = AudioStore.cacheLimitGb
    var confirmClear by remember { mutableStateOf(false) }

    val limitText = if (limit <= 0) "illimitata" else "$limit GB"
    Text(
        "In cache: ${formatBytes(AudioStore.cacheUsedBytes)} (limite $limitText)",
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        "Brani completi: ${AudioStore.cachedIds.size} · parziali: ${AudioStore.partialCount}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AudioStore.lastFillError?.let {
        Text(
            "Ultimo errore di salvataggio: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Text(
        "Scaricati: ${formatBytes(AudioStore.downloadsUsedBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(1 to "1 GB", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB", 0 to "Illimitata").forEach { (gb, label) ->
            FilterChip(
                selected = limit == gb,
                onClick = { AudioStore.setCacheLimit(gb) },
                label = { Text(label) }
            )
        }
    }

    OutlinedButton(onClick = { confirmClear = true }, enabled = AudioStore.cacheUsedBytes > 0) {
        Text(tr("Svuota cache"))
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Svuotare la cache?")) },
            text = { Text(tr("I brani in cache verranno eliminati. I brani scaricati restano.")) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmClear = false
                    AudioStore.clearCache()
                }) { Text(tr("Svuota")) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text(tr("Annulla")) }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.getDefault(), "%d MB", bytes / (1024 * 1024))
    else -> String.format(java.util.Locale.getDefault(), "%d KB", bytes / 1024)
}
