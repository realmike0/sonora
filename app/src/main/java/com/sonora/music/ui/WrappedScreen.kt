@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.sonora.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.PlayLog
import com.sonora.music.PlayerViewModel
import com.sonora.music.Tracking
import com.sonora.music.WrappedStats
import java.time.LocalDate

private val MONTHS = listOf("", "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
    "agosto", "settembre", "ottobre", "novembre", "dicembre")

private val PALETTES = listOf(
    Color(0xFF6A1B9A) to Color(0xFFFF6E40),
    Color(0xFF00695C) to Color(0xFFFFD54F),
    Color(0xFF1A237E) to Color(0xFF00E5FF),
    Color(0xFFAD1457) to Color(0xFFFFAB40),
    Color(0xFF283593) to Color(0xFFE040FB),
    Color(0xFF2E7D32) to Color(0xFF69F0AE)
)

@Composable
fun WrappedScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val back = predictiveBack(onBack = onClose)
    val context = LocalContext.current
    val thisYear = LocalDate.now().year
    var year by remember { mutableStateOf(thisYear) }
    var stats by remember { mutableStateOf<WrappedStats?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(year, Tracking.state) {
        stats = null
        note = null
        val local = WrappedStats.fromLog(year, PlayLog.all())
        if (Tracking.state == "accepted") {
            stats = try {
                Tracking.serverWrapped(context, year)
            } catch (e: Exception) {
                note = "Server non raggiungibile: dati solo di questo telefono."
                local
            }
        } else {
            note = "Calcolato dagli ascolti salvati su questo telefono."
            stats = local
        }
    }

    Box(Modifier.backEffect(back).fillMaxSize().background(Color(0xFF0E0E12))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = tr("Chiudi"), tint = Color.White) }
                // Un anno in più ogni 1 gennaio: da quello del primo ascolto fino a quello in corso.
                val first = remember(stats) {
                    PlayLog.all().minOfOrNull { java.time.Instant.ofEpochMilli(it.ts).atZone(java.time.ZoneId.systemDefault()).year }
                        ?.coerceAtMost(thisYear) ?: thisYear
                }
                Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState(), reverseScrolling = true), horizontalArrangement = Arrangement.End) {
                    for (y in first..thisYear) {
                        FilterChip(selected = y == year, onClick = { year = y }, label = { Text("$y") }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
            }
            val s = stats
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BouncyLoader() }
            } else if (s.plays == 0) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Nessun ascolto nel $year. Ascolta qualcosa e torna qui!", color = Color.White, textAlign = TextAlign.Center)
                }
            } else {
                val pages = 5
                val pager = rememberPagerState { pages }
                HorizontalPager(pager, Modifier.weight(1f)) { i ->
                    val (a, b) = PALETTES[i % PALETTES.size]
                    Box(
                        Modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(36.dp))
                            .background(Brush.linearGradient(listOf(a, b)))
                            .padding(28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        when (i) {
                            0 -> Big("Nel $year hai ascoltato", "${s.minutes}", "minuti di musica · ${s.plays} ascolti")
                            1 -> Big("Il tuo artista del $year", s.topArtists.firstOrNull()?.name ?: "-",
                                "${s.topArtists.firstOrNull()?.minutes ?: 0} minuti")
                            2 -> Ranking("I tuoi brani top", s.topTracks.map { it.title to "${it.plays}×" })
                            3 -> Ranking("I tuoi artisti top", s.topArtists.map { it.name to "${it.minutes} min" })
                            else -> Big(
                                "Il tuo momento",
                                if (s.topHour >= 0) "%02d:00".format(s.topHour) else "-",
                                (if (s.topMonth in 1..12) "Mese top: ${MONTHS[s.topMonth]} · " else "") + "${s.distinctTracks} brani diversi"
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages) {
                        Box(
                            Modifier.padding(3.dp).size(if (pager.currentPage == it) 10.dp else 6.dp)
                                .clip(CircleShape).background(Color.White.copy(alpha = if (pager.currentPage == it) 1f else 0.4f))
                        )
                    }
                }
            }
            note?.let { Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun Big(top: String, big: String, bottom: String) {
    Column {
        Text(top, color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text(big, color = Color.White, fontSize = if (big.length > 8) 44.sp else 72.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
        Spacer(Modifier.height(12.dp))
        Text(bottom, color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
    }
}

@Composable
private fun Ranking(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        rows.forEachIndexed { i, (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Color.White.copy(alpha = 0.7f), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                Text(name, color = Color.White, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(v, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
    }
}
