@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sonora.music.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sonora.music.Http
import com.sonora.music.Lyrics
import com.sonora.music.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** Pulsante + foglio per scegliere le righe e trasformarle in un'immagine da condividere. */
@Composable
fun LyricShareButton(vm: PlayerViewModel, lyrics: Lyrics, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Rounded.Share, contentDescription = tr("Crea immagine dal testo"), tint = androidx.compose.ui.graphics.Color.White)
    }
    if (open) LyricShareSheet(vm, lyrics) { open = false }
}

@Composable
private fun LyricShareSheet(vm: PlayerViewModel, lyrics: Lyrics, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<Int>() }
    var busy by remember { mutableStateOf(false) }
    val np = vm.nowPlaying

    ModalBottomSheet(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(tr("Scegli fino a 8 righe"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(lyrics.lines) { i, line ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (i in selected) selected.remove(i) else if (selected.size < 8) selected.add(i)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = i in selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(line.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = selected.isNotEmpty() && np != null && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val text = selected.sorted().joinToString("\n") { lyrics.lines[it].text }
                        val uri = withContext(Dispatchers.IO) {
                            runCatching { LyricImage.create(context, np!!.title, np.artist, np.artwork, text) }.getOrNull()
                        }
                        busy = false
                        if (uri != null) {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("image/png")
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    "Condividi immagine"
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) { Text(if (busy) "Creo l'immagine…" else "Crea immagine") }
        }
    }
}

/** Immagine in stile YouTube Music / Spotify: scheda tinta sul colore della copertina, titolo, righe grandi e logo. */
object LyricImage {
    private const val W = 1080
    private const val PAD = 56f

    fun create(context: Context, title: String, artist: String, coverUrl: String?, lyrics: String): android.net.Uri {
        val cover = coverUrl?.let { runCatching { download(it) }.getOrNull() }

        // Tinta dalla copertina (colore medio); se manca, dall'artista.
        val base = cover?.let { averageColor(it) } ?: artistColor(artist).toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base, hsv)
        val hue = hsv[0]
        val bgColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.24f))
        val textColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.28f, 0.96f))
        val softColor = android.graphics.Color.HSVToColor(160, floatArrayOf(hue, 0.3f, 0.9f))
        val lineColor = android.graphics.Color.HSVToColor(60, floatArrayOf(hue, 0.3f, 0.9f))

        val lines = lyrics.split("\n").filter { it.isNotBlank() }
        val textW = (W - 2 * PAD).toInt()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = Typeface.DEFAULT_BOLD }
        var size = 66f
        var layouts: List<StaticLayout>
        while (true) {
            paint.textSize = size
            layouts = lines.map {
                StaticLayout.Builder.obtain(it, 0, it.length, paint, textW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.12f).build()
            }
            if (layouts.sumOf { it.height } + 34 * layouts.size <= 1300 || size <= 36f) break
            size -= 4f
        }
        val gap = 34f
        val coverSize = 180f
        val headerBottom = PAD + coverSize
        val textTop = headerBottom + 50f + 16f
        val textHeight = layouts.sumOf { it.height }.toFloat() + gap * (layouts.size - 1)
        val footerTop = textTop + textHeight + 56f
        val height = (footerTop + 64f + PAD).toInt()

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        c.drawRoundRect(RectF(0f, 0f, W.toFloat(), height.toFloat()), 48f, 48f, card)

        val box = RectF(PAD, PAD, PAD + coverSize, PAD + coverSize)
        if (cover != null) {
            val clip = Path().apply { addRoundRect(box, 24f, 24f, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            c.drawBitmap(cover, null, box, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }

        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = Typeface.DEFAULT_BOLD; textSize = 44f }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = softColor; textSize = 42f }
        val tx = PAD + coverSize + 36f
        c.drawText(ellipsize(title, name, W - tx - PAD), tx, PAD + 84f, name)
        c.drawText(ellipsize(artist, sub, W - tx - PAD), tx, PAD + 142f, sub)

        val divider = Paint().apply { color = lineColor; strokeWidth = 3f }
        c.drawLine(PAD, headerBottom + 50f, W - PAD, headerBottom + 50f, divider)

        var y = textTop
        layouts.forEach {
            c.save()
            c.translate(PAD, y)
            it.draw(c)
            c.restore()
            y += it.height + gap
        }

        // Logo Sonora: cerchio con triangolo play + nome.
        val cy = footerTop + 32f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; style = Paint.Style.STROKE; strokeWidth = 6f }
        c.drawCircle(PAD + 32f, cy, 29f, ring)
        val tri = Path().apply {
            moveTo(PAD + 24f, cy - 15f); lineTo(PAD + 24f, cy + 15f); lineTo(PAD + 48f, cy); close()
        }
        c.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor })
        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; typeface = Typeface.DEFAULT_BOLD; textSize = 54f }
        c.drawText("Sonora", PAD + 82f, cy + 19f, mark)

        val dir = File(context.cacheDir, "lyrics").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "testo-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun averageColor(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0L; var g = 0L; var bl = 0L
        for (x in 0 until 16) for (y in 0 until 16) {
            val p = s.getPixel(x, y)
            r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); bl += android.graphics.Color.blue(p)
        }
        return android.graphics.Color.rgb((r / 256).toInt(), (g / 256).toInt(), (bl / 256).toInt())
    }

    private fun ellipsize(s: String, p: TextPaint, w: Float) =
        android.text.TextUtils.ellipsize(s, p, w, android.text.TextUtils.TruncateAt.END).toString()

    private fun download(url: String): Bitmap? {
        val r = Http.client.newCall(Request.Builder().url(url).build()).execute()
        return r.use { if (it.isSuccessful) BitmapFactory.decodeStream(it.body?.byteStream()) else null }
    }
}
